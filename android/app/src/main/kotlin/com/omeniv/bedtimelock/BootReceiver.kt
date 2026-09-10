package com.omeniv.bedtimelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> {
                val store = BedtimeStateStore(context)
                if (store.sessionActive) {
                    store.clearNotificationSweep()
                    if (store.lockScreenEnabled) store.setLockPending(true)
                }
                BedtimeCoordinator.restore(context)
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> BedtimeCoordinator.restore(context)
        }
    }
}
