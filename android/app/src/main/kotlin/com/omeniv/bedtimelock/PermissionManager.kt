package com.omeniv.bedtimelock

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

object PermissionManager {
    fun status(context: Context): Map<String, Any> {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return mapOf(
            "accessibility" to accessibilityGranted(context),
            "overlay" to Settings.canDrawOverlays(context),
            "exactAlarm" to ScheduleManager(context).canScheduleExactAlarms(),
            "battery" to (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true),
            "notifications" to NotificationManagerCompat.from(context).areNotificationsEnabled(),
            "notificationAccess" to notificationAccessGranted(context),
            "notificationPolicy" to (context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true),
            "displayControl" to Settings.System.canWrite(context),
        )
    }

    fun open(context: Context, capability: String) {
        when (capability) {
            "accessibility" -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addNewTask())
            "overlay" -> context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)).addNewTask())
            "exactAlarm" -> ScheduleManager(context).openExactAlarmSettings()
            "battery" -> {
                val powerIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)).addNewTask()
                context.startActivity(powerIntent)
            }
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context is Activity) {
                    context.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
                } else {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addNewTask()
                    })
                }
            }
            "notificationAccess" -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addNewTask())
            "notificationPolicy" -> context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addNewTask())
            "displayControl" -> context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri(context)).addNewTask())
        }
    }

    private fun notificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun accessibilityGranted(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(context, BedtimeAccessibilityService::class.java).flattenToString()
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name).flattenToString() == expected } }
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun Intent.addNewTask(): Intent = apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    const val NOTIFICATION_REQUEST_CODE = 912
}
