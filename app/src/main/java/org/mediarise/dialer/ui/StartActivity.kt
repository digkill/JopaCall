// app/src/main/java/com/example/dialer/ui/StartActivity.kt (запуск с roomId)
package org.mediarise.dialer.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class StartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, CallActivity::class.java).putExtra("room", "room-${System.currentTimeMillis()}"))
        finish()
    }
}
