package com.omeniv.bedtimelock

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private lateinit var channel: MethodChannel
    private val handler = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        channel.setMethodCallHandler { call, result ->
            val store = BedtimeStateStore(this)
            when (call.method) {
                "getState" -> result.success(store.asMap())
                "getPermissionStatus" -> result.success(PermissionManager.status(this))
                "saveConfiguration" -> {
                    val args = call.arguments as? Map<*, *>
                    store.saveConfiguration(
                        bedtimeHour = args.int("bedtimeHour", 22),
                        bedtimeMinute = args.int("bedtimeMinute", 0),
                        wakeHour = args.int("wakeHour", 5),
                        wakeMinute = args.int("wakeMinute", 0),
                        partnerName = args.string("partnerName"),
                        partnerPhone = args.string("partnerPhone"),
                        pin = args.string("pin"),
                    )
                    ScheduleManager(this).scheduleNextBedtime()
                    result.success(store.asMap())
                }
                "updatePartner" -> {
                    val args = call.arguments as? Map<*, *>
                    store.updatePartner(args.string("partnerName"), args.string("partnerPhone"), args.optionalString("pin"))
                    result.success(store.asMap())
                }
                "verifyPin" -> {
                    val verification = store.verifyPin((call.arguments as? Map<*, *>)?.string("pin").orEmpty())
                    result.success(mapOf("verified" to verification.verified, "message" to verification.message))
                }
                "setSchedule" -> {
                    val args = call.arguments as? Map<*, *>
                    if (store.sessionActive) {
                        result.success(store.asMap())
                    } else {
                        store.setSchedule(args.int("bedtimeHour", 22), args.int("bedtimeMinute", 0), args.int("wakeHour", 5), args.int("wakeMinute", 0))
                        ScheduleManager(this).scheduleNextBedtime()
                        result.success(store.asMap())
                    }
                }
                "setScheduleEnabled" -> {
                    val enabled = (call.arguments as? Map<*, *>)?.boolean("enabled") ?: true
                    if (store.sessionActive) {
                        result.success(store.asMap())
                    } else {
                        store.setScheduleEnabled(enabled)
                        if (enabled) ScheduleManager(this).scheduleNextBedtime() else ScheduleManager(this).cancelBedtime()
                        result.success(store.asMap())
                    }
                }
                "setEffects" -> {
                    val args = call.arguments as? Map<*, *>
                    store.setEffects(
                        doNotDisturb = args.booleanOrDefault("doNotDisturb", store.doNotDisturbEnabled),
                        hideNotifications = args.booleanOrDefault("hideNotifications", store.hideNotificationsEnabled),
                        dimScreen = args.booleanOrDefault("dimScreen", store.dimScreenEnabled),
                        bedtimeBrightnessPercent = args.int("bedtimeBrightnessPercent", store.bedtimeBrightnessPercent),
                        lockScreen = args.booleanOrDefault("lockScreen", store.lockScreenEnabled),
                    )
                    BedtimeEffectsController.applyCurrentState(this)
                    if (store.sessionActive && store.hideNotificationsEnabled && store.temporaryUnlockExpiry == 0L) {
                        BedtimeNotificationListenerService.snoozeCurrentNotifications(this)
                    }
                    result.success(store.asMap())
                }
                "openCapability" -> {
                    val capability = (call.arguments as? Map<*, *>)?.string("capability").orEmpty()
                    PermissionManager.open(this, capability)
                    result.success(null)
                }
                "lockNow" -> {
                    BedtimeCoordinator.lockNow(this)
                    result.success(null)
                }
                "refresh" -> {
                    BedtimeCoordinator.restore(this)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::channel.isInitialized) {
            handler.postDelayed({
                if (!isFinishing) channel.invokeMethod("permissionChanged", PermissionManager.status(this))
            }, 350)
        }
    }

    companion object {
        private const val CHANNEL = "com.omeniv.bedtimelock/platform"
    }
}

private fun Map<*, *>?.string(key: String): String = this?.get(key)?.toString().orEmpty()
private fun Map<*, *>?.optionalString(key: String): String? = this?.get(key)?.toString()?.takeIf { it.isNotBlank() }
private fun Map<*, *>?.int(key: String, fallback: Int): Int = (this?.get(key) as? Number)?.toInt() ?: fallback
private fun Map<*, *>?.boolean(key: String): Boolean = this?.get(key) as? Boolean ?: false
private fun Map<*, *>?.booleanOrDefault(key: String, fallback: Boolean): Boolean = this?.get(key) as? Boolean ?: fallback
