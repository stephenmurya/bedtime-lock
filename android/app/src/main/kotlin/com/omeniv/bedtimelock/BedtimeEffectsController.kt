package com.omeniv.bedtimelock

import android.content.Context
import android.provider.Settings

object BedtimeEffectsController {
    fun applyCurrentState(context: Context) {
        val store = BedtimeStateStore(context)
        if (!store.sessionActive) {
            BedtimeDndController.disable(context)
            restoreBrightness(context, store, clearAfter = true)
            return
        }

        val temporaryAccess = store.temporaryUnlockExpiry > System.currentTimeMillis()
        if (temporaryAccess) {
            BedtimeDndController.disable(context)
            restoreBrightness(context, store, clearAfter = false)
            return
        }

        if (store.doNotDisturbEnabled) BedtimeDndController.apply(context) else BedtimeDndController.disable(context)
        if (store.dimScreenEnabled) applyBrightness(context, store) else restoreBrightness(context, store, clearAfter = false)
    }

    fun endSession(context: Context) {
        val store = BedtimeStateStore(context)
        BedtimeDndController.disable(context)
        restoreBrightness(context, store, clearAfter = true)
    }

    private fun applyBrightness(context: Context, store: BedtimeStateStore) {
        if (!Settings.System.canWrite(context)) {
            NativeLog.d("bedtime brightness unavailable - write settings access missing")
            return
        }
        val resolver = context.contentResolver
        try {
            val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
            val brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            store.saveOriginalBrightness(brightness, mode)
            if (mode != Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            }
            val target = (255 * (store.bedtimeBrightnessPercent / 100f)).toInt().coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
            NativeLog.d("bedtime brightness applied")
        } catch (_: Settings.SettingNotFoundException) {
            NativeLog.d("bedtime brightness unavailable - setting not found")
        } catch (_: SecurityException) {
            NativeLog.d("bedtime brightness rejected by system")
        }
    }

    private fun restoreBrightness(context: Context, store: BedtimeStateStore, clearAfter: Boolean) {
        if (!store.originalBrightnessSaved) return
        if (!Settings.System.canWrite(context)) {
            NativeLog.d("original brightness pending - write settings access missing")
            return
        }
        val resolver = context.contentResolver
        try {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, store.originalBrightness)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, store.originalBrightnessMode)
            if (clearAfter) store.clearOriginalBrightness()
            NativeLog.d("original brightness restored")
        } catch (_: SecurityException) {
            NativeLog.d("original brightness restore rejected by system")
        }
    }
}
