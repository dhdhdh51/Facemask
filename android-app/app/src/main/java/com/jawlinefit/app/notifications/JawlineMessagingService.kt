package com.jawlinefit.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jawlinefit.app.JawlineFitApplication
import com.jawlinefit.app.R
import com.jawlinefit.app.data.remote.DeviceTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JawlineMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val api = (application as JawlineFitApplication).container.contentApi
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { api.registerDeviceToken(DeviceTokenRequest(token)) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        getSystemService(NotificationManager::class.java).notify(
            message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, REMINDER_CHANNEL)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(title.take(80))
                .setContentText(body.take(180))
                .setAutoCancel(true)
                .build()
        )
    }
}
