package org.mediarise.dialer.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*

class PeerConnectionManager(
    private val appContext: Context,
    private val factory: PeerConnectionFactory,    private val iceServers: List<PeerConnection.IceServer>,
    private val eglCtx: EglBase.Context,
    private val remoteSink: VideoSink,
    private val localSink: VideoSink,
    private val enableVideo: Boolean,
    private val relayOnly: Boolean = false
) {
    private var pc: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    // Внутренний класс SdpObserver для логирования
    private class LoggingSdpObserver(private val tag: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {
            Log.d(tag, "onCreateSuccess")
        }
        override fun onSetSuccess() {
            Log.d(tag, "onSetSuccess")
        }
        override fun onCreateFailure(error: String?) {
            Log.e(tag, "onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(tag, "onSetFailure: $error")
        }
    }

    fun createPeer(
        onIce: (IceCandidate) -> Unit,
        onConnected: () -> Unit,
        onDisconnected: (reason: String?) -> Unit
    ) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            if (relayOnly) iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }

        pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d("PeerConnection", "ICE state changed: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> onConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> onDisconnected(newState.name)
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = onIce(candidate)
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.addSink(remoteSink)
            }
            @Deprecated("Plan B legacy")
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(dc: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        })

        if (pc == null) {
            throw IllegalStateException("PeerConnection create failed. Check logs for WebRTC errors.")
        }

        // --- Инициализация локальных медиа-треков ---
        initLocalAudioTrack()
        if (enableVideo) {
            initLocalVideoTrack()
        }
    }

    private fun initLocalAudioTrack() {
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("audio0", audioSource)
        pc?.addTrack(localAudioTrack) // Добавляем трек в PeerConnection
    }

    private fun initLocalVideoTrack() {
        surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglCtx)
        videoCapturer = createBestCapturer()?.also { capturer ->
            val source = factory.createVideoSource(capturer.isScreencast)
            videoSource = source
            capturer.initialize(surfaceHelper, appContext, source.capturerObserver)
            capturer.startCapture(640, 480, 30)

            localVideoTrack = factory.createVideoTrack("video0", source)
            localVideoTrack?.addSink(localSink)
            pc?.addTrack(localVideoTrack)
        }
        if (videoCapturer == null) {
            Log.w("PeerConnectionManager", "Video capturer could not be created. Video will not be sent.")
        }
    }

    fun createOffer(onSdpReady: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        pc?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc?.setLocalDescription(LoggingSdpObserver("createOffer"), desc)
                onSdpReady(desc)
            }
            override fun onSetSuccess() {}
            // --- ИСПРАВЛЕНИЕ ЗДЕСЬ ---
            override fun onCreateFailure(error: String?) {
                Log.e("createOffer", "Failed: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e("createOffer", "Set-Failed: $error")
            }
        }, constraints)
    }

    fun createAnswer(onSdpReady: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints()
        pc?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc?.setLocalDescription(LoggingSdpObserver("createAnswer"), desc)
                onSdpReady(desc)
            }
            override fun onSetSuccess() {}
            // --- И ИСПРАВЛЕНИЕ ЗДЕСЬ ---
            override fun onCreateFailure(error: String?) {
                Log.e("createAnswer", "Failed: $error")
            }
            override fun onSetFailure(error: String?) {
                Log.e("createAnswer", "Set-Failed: $error")
            }
        }, constraints)
    }

    fun setRemoteDescription(desc: SessionDescription) {
        pc?.setRemoteDescription(LoggingSdpObserver("setRemote"), desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        pc?.addIceCandidate(candidate)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun close() {
        // Остановка и освобождение в правильном порядке
        runCatching { videoCapturer?.stopCapture() }.onFailure { Log.e("PCM", "stopCapture failed", it) }
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrack?.removeSink(localSink)
        localVideoTrack?.dispose()
        localVideoTrack = null

        surfaceHelper?.dispose()
        surfaceHelper = null

        videoSource?.dispose()
        videoSource = null

        localAudioTrack?.dispose()
        localAudioTrack = null

        audioSource?.dispose()
        audioSource = null

        pc?.close()
        pc = null
    }

    private fun createBestCapturer(): VideoCapturer? {
        return if (Camera2Enumerator.isSupported(appContext)) {
            createCapturer(Camera2Enumerator(appContext))
        } else {
            createCapturer(Camera1Enumerator(true))
        }
    }

    private fun createCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return null
        return enumerator.createCapturer(deviceName, null)
    }
}
