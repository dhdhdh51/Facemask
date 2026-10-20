package com.jawlinefit.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jawlinefit.app.MainActivity
import com.jawlinefit.app.R
import com.jawlinefit.app.data.local.UserPreferences
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val REMINDER_CHANNEL = "routine_reminders"
private const val HOUR_KEY = "local_hour"
private const val MINUTE_KEY = "local_minute"

class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val settings = UserPreferences(applicationContext).settings.first()
        if (!settings.remindersEnabled) return Result.success()

        if (android.os.Build.VERSION.SDK_INT < 33 ||
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            val openApp = PendingIntent.getActivity(
                applicationContext,
                1101,
                Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            applicationContext.getSystemService(NotificationManager::class.java).notify(
                1101,
                NotificationCompat.Builder(applicationContext, REMINDER_CHANNEL)
                    .setSmallIcon(R.drawable.ic_logo)
                    .setContentTitle(applicationContext.getString(R.string.reminder_title))
                    .setContentText(applicationContext.getString(R.string.reminder_body))
                    .setContentIntent(openApp)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
            )
        }

        // Recalculate from civil time after every firing so DST and timezone changes are respected.
        ReminderScheduler.setEnabled(applicationContext, true, settings.reminderHour, settings.reminderMinute)
        return Result.success()
    }
}

object ReminderScheduler {
    private const val WORK_NAME = "jawlinefit_daily_reminder"

    fun setEnabled(context: Context, enabled: Boolean, hour: Int, minute: Int) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val delayMillis = millisUntilNextLocalTime(safeHour, safeMinute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(Data.Builder().putInt(HOUR_KEY, safeHour).putInt(MINUTE_KEY, safeMinute).build())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun createChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notification_channel_description) }
        )
    }
}

internal fun millisUntilNextLocalTime(hour: Int, minute: Int, nowMillis: Long = System.currentTimeMillis()): Long {
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val next = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(Calendar.MINUTE, minute.coerceIn(0, 59))
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    return (next.timeInMillis - now.timeInMillis).coerceAtLeast(1L)
}

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = UserPreferences(context.applicationContext).settings.first()
                ReminderScheduler.setEnabled(context, settings.remindersEnabled, settings.reminderHour, settings.reminderMinute)
            } finally {
                pending.finish()
            }
        }
    }
}
