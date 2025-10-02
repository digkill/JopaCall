// app/src/main/java/com/example/dialer/RtcEnv.kt
package com.example.dialer

import android.app.Application
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * Глобальное WebRTC-окружение: общий EGL-контекст и одна фабрика.
 */
object RtcEnv {
    private lateinit var eglBase: EglBase
    val eglCtx: EglBase.Context get() = eglBase.eglBaseContext

    lateinit var factory: PeerConnectionFactory
        private set

    @Volatile private var initialized = false

    fun init(app: Application) {
        if (initialized) return

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(app)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        val encoderFactory = DefaultVideoEncoderFactory(eglCtx, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglCtx)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        initialized = true
    }

    fun release() {
        if (!initialized) return
        eglBase.release()
        initialized = false
    }
}
