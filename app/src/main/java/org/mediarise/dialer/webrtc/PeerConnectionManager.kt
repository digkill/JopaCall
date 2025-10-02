// app/src/main/java/org/mediarise/dialer/webrtc/PeerConnectionManager.kt
package org.mediarise.dialer.webrtc

import android.content.Context
import org.webrtc.*

class PeerConnectionManager(
    private val appContext: Context,
    private val factory: PeerConnectionFactory,
    private val iceServers: List<PeerConnection.IceServer>,
    private val eglCtx: EglBase.Context,            // <- добавили
    private val remoteSink: VideoSink,
    private val localSink: VideoSink,
    private val enableVideo: Boolean,
    private val relayOnly: Boolean = true
) {
    // УБРАЛИ private val egl = EglBase.create()

    private var pc: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioTrack: AudioTrack? = null
    private var capturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    fun createPeer(
        onIce: (IceCandidate) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: (reason: String?) -> Unit
    ) {
        val cfg = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            if (relayOnly) iceTransportsType = PeerConnection.IceTransportsType.RELAY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
        }

        val created = factory.createPeerConnection(cfg, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED -> onConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> onDisconnected(newState.name)
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) { onIce(candidate) }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit

            @Deprecated("Plan B legacy")
            override fun onAddStream(stream: MediaStream) {
                if (stream.videoTracks.isNotEmpty()) stream.videoTracks[0].addSink(remoteSink)
            }
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(dc: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.addSink(remoteSink)
            }
        })

        if (created == null) {
            // Больше информации в лог — поможет, если всё ещё null
            throw IllegalStateException("PeerConnection create failed (check: App class, ADM, EGL ctx, ICE servers)")
        }
        pc = created

        // AUDIO
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio0", audioSource)
        requireNotNull(pc).addTransceiver(
            audioTrack,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        )

        // VIDEO
        if (enableVideo) {
            videoSource = factory.createVideoSource(false)
            surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglCtx)  // <- общий контекст!
            capturer = createBestCapturer()?.also { cap ->
                runCatching {
                    cap.initialize(surfaceHelper, appContext, videoSource!!.capturerObserver)
                    cap.startCapture(640, 480, 30)

                    videoTrack = factory.createVideoTrack("video0", videoSource!!)
                    videoTrack?.addSink(localSink)

                    requireNotNull(pc).addTransceiver(
                        videoTrack,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                    )
                }.onFailure {
                    runCatching { cap.stopCapture() }
                    cap.dispose()
                    capturer = null
                    videoTrack?.dispose(); videoTrack = null
                    videoSource?.dispose(); videoSource = null
                    surfaceHelper?.dispose(); surfaceHelper = null
                }
            }
        }
    }

    fun createOffer(onSdp: (SessionDescription) -> Unit) {
        val pcLocal = requireNotNull(pc)
        pcLocal.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pcLocal.setLocalDescription(LoggingSdpObserver(), desc)
                onSdp(desc)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(msg: String?) = Unit
            override fun onSetFailure(msg: String?) = Unit
        }, MediaConstraints())
    }

    fun createAnswer(onSdp: (SessionDescription) -> Unit) {
        val pcLocal = requireNotNull(pc)
        pcLocal.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pcLocal.setLocalDescription(LoggingSdpObserver(), desc)
                onSdp(desc)
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(msg: String?) = Unit
            override fun onSetFailure(msg: String?) = Unit
        }, MediaConstraints())
    }

    fun setRemote(desc: SessionDescription) {
        requireNotNull(pc).setRemoteDescription(LoggingSdpObserver(), desc)
    }

    fun addIce(candidate: IceCandidate) {
        requireNotNull(pc).addIceCandidate(candidate)
    }

    fun switchCamera() {
        (capturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun close() {
        runCatching { (capturer as? CameraVideoCapturer)?.stopCapture() }
        capturer?.dispose(); capturer = null

        videoTrack?.removeSink(localSink)
        videoTrack?.dispose(); videoTrack = null

        surfaceHelper?.dispose(); surfaceHelper = null
        videoSource?.dispose(); videoSource = null

        audioTrack?.dispose(); audioTrack = null
        audioSource?.dispose(); audioSource = null

        pc?.close(); pc = null
    }

    // ---- helpers ----
    private fun createBestCapturer(): VideoCapturer? {
        if (Camera2Enumerator.isSupported(appContext)) {
            val e = Camera2Enumerator(appContext)
            val front = e.deviceNames.firstOrNull { e.isFrontFacing(it) } ?: e.deviceNames.firstOrNull()
            if (front != null) e.createCapturer(front, null)?.let { return it }
        }
        val e1 = Camera1Enumerator(true)
        val front1 = e1.deviceNames.firstOrNull { e1.isFrontFacing(it) } ?: e1.deviceNames.firstOrNull()
        if (front1 != null) e1.createCapturer(front1, null)?.let { return it }
        return null
    }
}

class LoggingSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
