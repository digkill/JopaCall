package org.mediarise.dialer.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import org.mediarise.dialer.BuildConfig
import org.mediarise.dialer.RtcEnv
import org.mediarise.dialer.signaling.SignalingClient
import org.mediarise.dialer.webrtc.PeerConnectionManager
import org.webrtc.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class CallActivity : ComponentActivity(), SignalingClient.Listener {

    private lateinit var localView: SurfaceViewRenderer
    private lateinit var remoteView: SurfaceViewRenderer
    private var pcm: PeerConnectionManager? = null
    private val sig: SignalingClient by lazy {
        SignalingClient(BuildConfig.WS_BASE, lifecycleScope, this)
    }

    private val pcFactory get() = RtcEnv.factory
    private val eglCtx get() = RtcEnv.eglCtx
    private val roomId: String by lazy { intent?.getStringExtra("room") ?: "room-demo-${UUID.randomUUID().toString().take(4)}" }

    // Флаг для предотвращения повторного закрытия
    private val isClosing = AtomicBoolean(false)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true
            startCallFlow(cameraEnabled = cameraGranted)
        } else {
            Toast.makeText(this, "Требуется разрешение на микрофон.", Toast.LENGTH_LONG).show()
            safeFinish()
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Не даем экрану погаснуть во время звонка
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setupUI()
        requestNeededPermissions()
    }

    private fun setupUI() {
        val container = FrameLayout(this)
        remoteView = SurfaceViewRenderer(this).apply {
            init(eglCtx, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        localView = SurfaceViewRenderer(this).apply {
            init(eglCtx, null)
            setMirror(true)
            setEnableHardwareScaler(true)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setZOrderMediaOverlay(true)
            val w = 120.dp()
            val h = 160.dp()
            layoutParams = FrameLayout.LayoutParams(w, h, Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = 16.dp()
                bottomMargin = 16.dp()
            }
        }
        container.addView(remoteView)
        container.addView(localView)
        setContentView(container)
    }

    private fun requestNeededPermissions() {
        val requiredPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        requestPermissionsLauncher.launch(requiredPermissions)
    }

    private fun startCallFlow(cameraEnabled: Boolean) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                .setUsername(BuildConfig.TURN_USER)
                .setPassword(BuildConfig.TURN_PASS)
                .createIceServer()
        )

        pcm = PeerConnectionManager(
            appContext = applicationContext,
            factory = pcFactory,
            eglCtx = eglCtx,
            iceServers = iceServers,
            remoteSink = remoteView,
            localSink = localView,
            enableVideo = cameraEnabled
        ).also { manager ->
            manager.createPeer(
                onIce = { candidate -> sig.sendIce(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp) },
                onConnected = { runOnUiThread { Toast.makeText(this, "Соединено!", Toast.LENGTH_SHORT).show() } },
                // --- УЛУЧШЕНИЕ: Используем общий метод onClosed ---
                onDisconnected = { reason -> onClosed("Соединение разорвано: $reason") }
            )
        }
        sig.connect(roomId)
    }

    // --- Signaling Callbacks ---

    override fun onPeerJoined(peerId: String) {
        // Убедимся, что pcm не null, прежде чем создавать offer
        pcm?.createOffer { sdp -> sig.sendOffer(sdp.description) }
    }

    override fun onOffer(from: String, sdp: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pcm?.setRemoteDescription(remoteSdp)
        pcm?.createAnswer { answerSdp -> sig.sendAnswer(answerSdp.description) }
    }

    override fun onAnswer(from: String, sdp: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pcm?.setRemoteDescription(remoteSdp)
    }

    override fun onIce(from: String, mid: String, index: Int, cand: String) {
        pcm?.addIceCandidate(IceCandidate(mid, index, cand))
    }

    override fun onClosed(reason: String) {
        // Показываем сообщение и безопасно закрываем активность
        runOnUiThread {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            safeFinish()
        }
    }

    // --- УЛУЧШЕНИЕ: Безопасное завершение активности ---
    private fun safeFinish() {
        // AtomicBoolean гарантирует, что код внутри выполнится только один раз
        if (isClosing.compareAndSet(false, true)) {
            Log.d("CallActivity", "Finishing activity...")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CallActivity", "onDestroy called")
        // Очищаем флаг, чтобы экран не оставался включенным
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Безопасно освобождаем все ресурсы
        sig.leave()
        pcm?.close()
        // Очищаем рендереры в последнюю очередь
        localView.release()
        remoteView.release()
    }
}
