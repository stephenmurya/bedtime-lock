package com.omeniv.bedtimelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TemporaryUnlockExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BedtimeCoordinator.expireTemporaryAccess(context)
    }
}
