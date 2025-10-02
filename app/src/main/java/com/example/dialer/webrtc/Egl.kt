// app/src/main/java/com/example/dialer/webrtc/Egl.kt
package com.example.dialer.webrtc

import org.webrtc.EglBase

object Egl {
    val instance: EglBase by lazy { EglBase.create() }
}
