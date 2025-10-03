package org.mediarise.dialer

import android.app.Application

/**
 * Кастомный класс Application для управления жизненным циклом приложения
 * и инициализации глобальных компонентов, таких как WebRTC.
 */
class App : Application() {

  override fun onCreate() {
    super.onCreate()

    // Вызываем централизованный метод инициализации из RtcEnv.
    // Передаем контекст приложения, который нужен для инициализации.
    RtcEnv.init(this)
  }

  override fun onTerminate() {
    super.onTerminate()

    // Крайне важно освобождать нативные ресурсы WebRTC при завершении работы приложения,
    // чтобы избежать утечек памяти.
    RtcEnv.release()
  }
}
