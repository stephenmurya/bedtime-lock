package com.omeniv.bedtimelock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class ScheduleManager(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleNextBedtime() {
        val store = BedtimeStateStore(context)
        if (!store.scheduleEnabled || !store.setupComplete) {
            cancelBedtime()
            return
        }
        val next = ScheduleCalculator.nextBedtime(
            bedtimeHour = store.bedtimeHour,
            bedtimeMinute = store.bedtimeMinute,
            wakeHour = store.wakeHour,
            wakeMinute = store.wakeMinute,
        )
        scheduleExact(BEDTIME_REQUEST_CODE, next.toInstant().toEpochMilli(), BedtimeAlarmReceiver::class.java)
        NativeLog.d("bedtime scheduled")
    }

    fun scheduleWake(timestamp: Long) {
        scheduleExact(WAKE_REQUEST_CODE, timestamp, WakeAlarmReceiver::class.java)
    }

    fun scheduleTemporaryExpiry(timestamp: Long) {
        scheduleExact(TEMPORARY_REQUEST_CODE, timestamp, TemporaryUnlockExpiryReceiver::class.java)
    }

    fun cancelBedtime() = cancel(BEDTIME_REQUEST_CODE, BedtimeAlarmReceiver::class.java)

    fun cancelWake() = cancel(WAKE_REQUEST_CODE, WakeAlarmReceiver::class.java)

    fun cancelTemporaryExpiry() = cancel(TEMPORARY_REQUEST_CODE, TemporaryUnlockExpiryReceiver::class.java)

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun scheduleExact(requestCode: Int, timestamp: Long, receiver: Class<*>) {
        if (timestamp <= System.currentTimeMillis()) return
        if (!canScheduleExactAlarms()) {
            NativeLog.d("exact alarm permission unavailable")
            return
        }
        val intent = Intent(context, receiver)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
            }
        } catch (_: SecurityException) {
            pendingIntent.cancel()
            NativeLog.d("exact alarm scheduling rejected")
        }
    }

    private fun cancel(requestCode: Int, receiver: Class<*>) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, receiver),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    companion object {
        const val BEDTIME_REQUEST_CODE = 1201
        const val WAKE_REQUEST_CODE = 1202
        const val TEMPORARY_REQUEST_CODE = 1203
    }
}
