// app/src/main/java/com/example/dialer/ui/CallActivity.kt
package com.example.dialer.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.dialer.BuildConfig
import com.example.dialer.RtcEnv
import com.example.dialer.service.CallService
import com.example.dialer.signaling.SignalingClient
import com.example.dialer.webrtc.PeerConnectionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.RendererCommon
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer

class CallActivity : ComponentActivity(), SignalingClient.Listener {

    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private var pcm: PeerConnectionManager? = null
    private lateinit var sig: SignalingClient

    private val pcFactory get() = RtcEnv.factory
    private val eglCtx get() = RtcEnv.eglCtx
    private val roomId: String by lazy { intent?.getStringExtra("room") ?: "room-demo" }

    private val reqPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val cam = grants[Manifest.permission.CAMERA] == true
        val mic = grants[Manifest.permission.RECORD_AUDIO] == true
        if (mic) startFlow(cameraAllowed = cam) else requestMicOnly()
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this)

        // Remote — на весь экран
        remoteView = SurfaceViewRenderer(this).apply {
            init(eglCtx, null)
            setMirror(false)
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            keepScreenOn = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Local — маленькое окно в правом нижнем углу поверх remote
        localView = SurfaceViewRenderer(this).apply {
            init(eglCtx, null)
            setMirror(true)
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            keepScreenOn = true
            setZOrderMediaOverlay(true)
            val w = 120.dp()
            val h = 160.dp()
            layoutParams = FrameLayout.LayoutParams(w, h, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = 12.dp()
                bottomMargin = 12.dp()
            }
            elevation = 8f
        }

        localView.setZOrderMediaOverlay(true)
        localView.setZOrderOnTop(true) // << вот это для "поверх"
        localView.setMirror(true)

        container.addView(remoteView)
        container.addView(localView)
        setContentView(container)

        // Signaling
        sig = SignalingClient(BuildConfig.WS_BASE, lifecycleScope, this)

        // Permissions
        val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        reqPerms.launch(perms.toTypedArray())
    }

    private fun requestMicOnly() {
        reqPerms.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
    }

    private fun startFlow(cameraAllowed: Boolean) {
        // FGS может не стартовать в эмуляторе — не критично
        runCatching {
            val intent = Intent(this, CallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(
                intent
            )
        }

        val ice = listOf(
            // оставляем только TURN, пока проверяешь соединение
            PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                .setUsername(BuildConfig.TURN_USER)
                .setPassword(BuildConfig.TURN_PASS)
                .createIceServer()
        )

        pcm = PeerConnectionManager(
            appContext = applicationContext,
            factory = pcFactory,
            iceServers = ice,
            remoteSink = remoteView,
            localSink = localView,
            relayOnly = true,
            enableVideo = cameraAllowed
        ).also { manager ->
            manager.createPeer(
                onIce = { c: IceCandidate -> sig.sendIce(c.sdpMid ?: "", c.sdpMLineIndex, c.sdp) },
                onConnected = { /* UI: connected */ },
                onDisconnected = { /* UI: reconnect */ }
            )
        }

        // Подключаемся, оффер пошлём после peer-joined
        sig.connect(roomId)
    }

    // --- Signaling callbacks ---
    override fun onOffer(from: String, sdp: String) {
        pcm?.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp))
        pcm?.createAnswer { ans -> sig.sendAnswer(ans.description) }
    }

    override fun onAnswer(from: String, sdp: String) {
        pcm?.setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    override fun onIce(from: String, mid: String, index: Int, cand: String) {
        pcm?.addIce(IceCandidate(mid, index, cand))
    }

    override fun onPeerJoined(peerId: String) {
        lifecycleScope.launch {
            delay(100)
            pcm?.createOffer { off -> sig.sendOffer(off.description) }
        }
    }

    override fun onClosed() {}

    override fun onDestroy() {
        super.onDestroy()
        runCatching { sig.leave() }
        pcm?.close(); pcm = null
        localView.release()
        remoteView.release()
    }
}
