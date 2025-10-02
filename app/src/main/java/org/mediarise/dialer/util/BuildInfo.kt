// app/src/main/java/com/example/dialer/util/BuildInfo.kt (удобный доступ к ICE)
package org.mediarise.dialer.util

import org.mediarise.dialer.BuildConfig

object BuildInfo {
    val wsBase: String get() = BuildConfig.WS_BASE
    val turnUrl: String get() = BuildConfig.TURN_URL
    val turnUser: String get() = BuildConfig.TURN_USER
    val turnPass: String get() = BuildConfig.TURN_PASS
}
