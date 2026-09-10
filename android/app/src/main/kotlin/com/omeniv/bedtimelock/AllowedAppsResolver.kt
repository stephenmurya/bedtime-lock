package com.omeniv.bedtimelock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

class AllowedAppsResolver(private val context: Context) {
    fun isAllowed(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName == context.packageName || packageName == "android" || packageName == "com.android.systemui") return true
        return packageName in allowedPackages()
    }

    private fun allowedPackages(): Set<String> {
        val packages = mutableSetOf<String>()
        resolvePackage(Intent(Intent.ACTION_DIAL))?.let(packages::add)
        resolvePackage(Intent(Intent.ACTION_ANSWER))?.let(packages::add)
        resolvePackage(Intent(AlarmClock.ACTION_SHOW_ALARMS))?.let(packages::add)
        resolvePackage(Intent(AlarmClock.ACTION_SET_ALARM))?.let(packages::add)
        resolvePackage(Intent(AlarmClock.ACTION_SHOW_TIMERS))?.let(packages::add)
        resolvePackage(Intent(AlarmClock.ACTION_SHOW_ALARMS))?.let(packages::add)
        return packages
    }

    private fun resolvePackage(intent: Intent): String? = context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
}
