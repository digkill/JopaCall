package org.mediarise.dialer

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class App : Application() {
  val appScope = CoroutineScope(SupervisorJob())

  override fun onCreate() {
    super.onCreate()
    // Единая инициализация WebRTC окружения
    RtcEnv.init(this)
  }
}
