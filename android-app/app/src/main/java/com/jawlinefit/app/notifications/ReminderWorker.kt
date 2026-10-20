package com.jawlinefit.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jawlinefit.app.R
import java.util.concurrent.TimeUnit

const val REMINDER_CHANNEL = "routine_reminders"

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(1101, NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("Time for your JawlineFit routine")
            .setContentText("Move gently and stop if you feel pain or dizziness.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build())
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "jawlinefit_daily_reminder"
    fun setEnabled(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (enabled) manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).setInitialDelay(12, TimeUnit.HOURS).build()
        ) else manager.cancelUniqueWork(WORK_NAME)
    }

    fun createChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(REMINDER_CHANNEL, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.notification_channel_description)
            }
        )
    }
}
