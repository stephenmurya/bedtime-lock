package com.omeniv.bedtimelock

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.service.notification.StatusBarNotification

object NotificationFilter {
    data class Metadata(
        val packageName: String,
        val flags: Int,
        val category: String?,
        val systemApp: Boolean,
    )

    data class Decision(val eligible: Boolean, val reason: String?)

    fun decision(metadata: Metadata, ownPackage: String): Decision {
        if (metadata.packageName == ownPackage) return Decision(false, "own")
        if (metadata.packageName == "android" || metadata.packageName == "com.android.systemui") {
            return Decision(false, "system")
        }
        if (metadata.systemApp) return Decision(false, "system")
        if (metadata.flags and Notification.FLAG_ONGOING_EVENT != 0) return Decision(false, "ongoing")
        if (metadata.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return Decision(false, "foreground service")
        when (metadata.category) {
            Notification.CATEGORY_CALL -> return Decision(false, "active call")
            Notification.CATEGORY_ALARM -> return Decision(false, "alarm")
            Notification.CATEGORY_SYSTEM -> return Decision(false, "system")
            Notification.CATEGORY_SERVICE -> return Decision(false, "service")
        }
        return Decision(true, null)
    }

    fun decision(context: Context, notification: StatusBarNotification): Decision {
        val packageName = notification.packageName
        val systemApp = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
        return decision(
            Metadata(
                packageName = packageName,
                flags = notification.notification.flags,
                category = notification.notification.category,
                systemApp = systemApp,
            ),
            context.packageName,
        )
    }
}
