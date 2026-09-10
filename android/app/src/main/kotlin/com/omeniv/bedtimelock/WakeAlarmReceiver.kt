package com.omeniv.bedtimelock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        BedtimeCoordinator.endBedtime(context)
    }
}
