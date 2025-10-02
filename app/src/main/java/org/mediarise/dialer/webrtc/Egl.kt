// app/src/main/java/com/example/dialer/webrtc/Egl.kt
package org.mediarise.dialer.webrtc

import org.webrtc.EglBase

object Egl {
    val instance: EglBase by lazy { EglBase.create() }
}
