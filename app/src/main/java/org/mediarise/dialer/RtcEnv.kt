package org.mediarise.dialer

import android.app.Application
import android.content.Context
import android.util.Log
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.Logging
import org.webrtc.PeerConnectionFactory
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Глобальный синглтон для хранения и управления ресурсами WebRTC.
 * Инициализируется один раз при старте приложения.
 */
object RtcEnv {
    private lateinit var eglBase: EglBase
    val eglCtx: EglBase.Context get() = eglBase.eglBaseContext

    lateinit var factory: PeerConnectionFactory
        private set

    // Убрал @Volatile, так как доступ к initialized будет только из главного потока
    private var initialized = false
    private val lock = Any()

    /**
     * Инициализирует все глобальные ресурсы WebRTC. Потокобезопасен.
     * @param app Контекст приложения.
     */
    fun init(app: Application) {
        synchronized(lock) {
            if (initialized) {
                Log.d("RtcEnv", "WebRTC-окружение уже инициализировано.")
                return
            }

            // 1. Создаем EGL-контекст для рендеринга видео
            eglBase = EglBase.create()

            // 2. Глобальная инициализация нативной части WebRTC с логгером
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(app)
                    .setEnableInternalTracer(true)
                    .setInjectableLogger({ message, severity, tag ->
                        val priority = when (severity) {
                            Logging.Severity.LS_ERROR -> Log.ERROR
                            Logging.Severity.LS_WARNING -> Log.WARN
                            Logging.Severity.LS_INFO -> Log.INFO
                            Logging.Severity.LS_VERBOSE -> Log.VERBOSE
                            else -> Log.DEBUG
                        }
                        Log.println(priority, "WebRTC/$tag", message)
                    }, Logging.Severity.LS_INFO)
                    .createInitializationOptions()
            )

            // 3. --- ГЛАВНОЕ ИСПРАВЛЕНИЕ: Отключаем аппаратное ускорение ---
            // Для максимальной совместимости, особенно на эмуляторах, отключаем аппаратное
            // ускорение для кодировщика видео. Это решит проблему EGL_BAD_ATTRIBUTE.
            val videoEncoderFactory = DefaultVideoEncoderFactory(
                eglCtx,
                false, /* enableIntelVp8Encoder */
                false  /* enableH264HighProfile */
            )
            val videoDecoderFactory = DefaultVideoDecoderFactory(eglCtx)

            // 4. Создание аудиомодуля
            val adm = JavaAudioDeviceModule.builder(app)
                // Включаем нативные обработчики звука, если они доступны на устройстве
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            // 5. Создание основной фабрики PeerConnection
            factory = PeerConnectionFactory
                .builder()
                .setVideoEncoderFactory(videoEncoderFactory)
                .setVideoDecoderFactory(videoDecoderFactory)
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()

            // Освобождаем аудиомодуль, так как фабрика уже получила на него ссылку
            // и будет управлять его жизненным циклом. Это предотвращает утечки.
            adm.release()

            initialized = true
            Log.d("RtcEnv", "WebRTC-окружение успешно инициализировано.")
        }
    }

    /**
     * Освобождает все глобальные ресурсы WebRTC.
     */
    fun release() {
        synchronized(lock) {
            if (!initialized) {
                return
            }
            factory.dispose()
            eglBase.release()
            PeerConnectionFactory.shutdownInternalTracer()
            initialized = false
            Log.d("RtcEnv", "WebRTC-окружение освобождено.")
        }
    }
}
