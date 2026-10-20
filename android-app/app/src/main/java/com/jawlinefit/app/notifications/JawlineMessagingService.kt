package com.jawlinefit.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.jawlinefit.app.JawlineFitApplication
import com.jawlinefit.app.MainActivity
import com.jawlinefit.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JawlineMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val repository = (application as JawlineFitApplication).container.contentRepository
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repository.registerDeviceToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val openApp = PendingIntent.getActivity(
            this,
            1201,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        getSystemService(NotificationManager::class.java).notify(
            message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(this, REMINDER_CHANNEL)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentTitle(title.take(80))
                .setContentText(body.take(180))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build()
        )
    }
}
