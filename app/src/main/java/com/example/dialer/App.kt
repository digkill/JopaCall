// app/src/main/java/com/example/dialer/App.kt
package com.example.dialer

import android.app.Application

class App : Application() {
  override fun onCreate() {
    super.onCreate()
    RtcEnv.init(this)   // ← инициализируем глобальное окружение
  }

  override fun onTerminate() {
    super.onTerminate()
    RtcEnv.release()
  }
}
