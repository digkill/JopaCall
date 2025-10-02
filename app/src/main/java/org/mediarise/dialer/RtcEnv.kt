package org.mediarise.dialer

import android.app.Application
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Глобальное WebRTC-окружение: общий EGL-контекст и одна фабрика.
 */
object RtcEnv {
    private var eglBase: EglBase? = null
    val eglCtx: EglBase.Context
        get() = requireNotNull(eglBase) { "RtcEnv not initialized" }.eglBaseContext

    val egl: EglBase
        get() = requireNotNull(eglBase) { "RtcEnv not initialized" }

    lateinit var factory: PeerConnectionFactory
        private set

    @Volatile private var initialized = false

    @Synchronized
    fun init(app: Application) {
        if (initialized) return

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        val adm = JavaAudioDeviceModule.builder(app)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(
            eglCtx,
            /* enableIntelVp8 = */ true,
            /* enableH264HighProfile = */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglCtx)

        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        initialized = true
    }

    @Synchronized
    fun release() {
        if (!initialized) return
        eglBase?.release()
        eglBase = null
        initialized = false
    }
}
