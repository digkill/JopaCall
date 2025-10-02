// app/src/main/java/com/example/dialer/service/CallService.kt
package com.example.dialer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.dialer.R

class CallService : Service() {
    private val channelId = "webrtc_call"

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        ensureChannel()

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Video Call")
            .setContentText("Connecting…")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // ВАЖНО: указать корректные типы FGS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // 34
            startForeground(
                42,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else // 29+
            startForeground(
                42,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(channelId) == null
        ) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Calls",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
