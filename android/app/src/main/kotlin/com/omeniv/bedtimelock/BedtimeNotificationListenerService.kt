package com.omeniv.bedtimelock

import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class BedtimeNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        NativeLog.d("notification listener connected")
        sweepCurrentSessionIfNeeded()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        snoozeIfNeeded(sbn)
    }

    fun sweepCurrentSessionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val store = BedtimeStateStore(this)
        val now = System.currentTimeMillis()
        if (!NotificationSnoozeCalculator.shouldSnooze(
                sessionActive = store.sessionActive,
                hideNotifications = store.hideNotificationsEnabled,
                wake = store.sessionWake,
                temporaryUnlockExpiry = store.temporaryUnlockExpiry,
                now = now,
            )) return
        if (store.notificationSweepWake == store.sessionWake) return
        val active: Array<StatusBarNotification> = runCatching { getActiveNotifications() }
            .getOrDefault(emptyArray())
        active.forEach { snoozeIfNeeded(it) }
        store.markNotificationSweep(store.sessionWake)
    }

    private fun snoozeIfNeeded(sbn: StatusBarNotification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val store = BedtimeStateStore(this)
        val now = System.currentTimeMillis()
        if (!NotificationSnoozeCalculator.shouldSnooze(
                sessionActive = store.sessionActive,
                hideNotifications = store.hideNotificationsEnabled,
                wake = store.sessionWake,
                temporaryUnlockExpiry = store.temporaryUnlockExpiry,
                now = now,
            )) return

        val decision = NotificationFilter.decision(this, sbn)
        if (!decision.eligible) {
            NativeLog.d("notification exempt: ${decision.reason}: package=${sbn.packageName}")
            return
        }
        val duration = NotificationSnoozeCalculator.remainingUntilWake(store.sessionWake, now)
        runCatching { snoozeNotification(sbn.key, duration) }
            .onSuccess { NativeLog.d("notification snoozed: package=${sbn.packageName}") }
            .onFailure { NativeLog.d("notification snooze rejected: package=${sbn.packageName}") }
    }

    companion object {
        @Volatile
        var instance: BedtimeNotificationListenerService? = null
            private set

        fun snoozeCurrentNotifications(context: android.content.Context) {
            instance?.sweepCurrentSessionIfNeeded()
                ?: NativeLog.d("notification listener unavailable")
        }
    }
}
