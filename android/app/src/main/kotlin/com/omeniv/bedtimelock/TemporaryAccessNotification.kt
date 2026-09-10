package com.omeniv.bedtimelock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object TemporaryAccessNotification {
    private const val CHANNEL_ID = "temporary_access"
    private const val NOTIFICATION_ID = 901

    fun show(context: Context, expiry: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        createChannel(manager)
        val actionIntent = PendingIntent.getBroadcast(
            context,
            901,
            android.content.Intent(context, LockNowReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Bedtime temporarily unlocked")
            .setContentText("15 minutes of access")
            .setWhen(expiry)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_lock_lock, "Lock now", actionIntent)
            .build()
        try {
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            NativeLog.d("notification permission missing")
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    private fun createChannel(manager: NotificationManager?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Temporary access", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows remaining bedtime access time"
                    setShowBadge(false)
                },
            )
        }
    }
}
