package com.omeniv.bedtimelock

import android.content.Context
import java.time.ZoneId
import java.time.ZonedDateTime

object BedtimeCoordinator {
    fun restore(context: Context) {
        val store = BedtimeStateStore(context)
        if (!store.setupComplete || !store.scheduleEnabled) {
            ScheduleManager(context).cancelBedtime()
            if (store.sessionActive) endBedtime(context)
            else BedtimeEffectsController.endSession(context)
            return
        }

        val now = System.currentTimeMillis()
        val activeWindow = ScheduleCalculator.activeWindow(
            now = ZonedDateTime.now(ZoneId.systemDefault()),
            bedtimeHour = store.bedtimeHour,
            bedtimeMinute = store.bedtimeMinute,
            wakeHour = store.wakeHour,
            wakeMinute = store.wakeMinute,
        )
        if (activeWindow == null) {
            if (store.sessionActive) endBedtime(context)
            else BedtimeEffectsController.endSession(context)
            ScheduleManager(context).scheduleNextBedtime()
            return
        }

        if (!store.sessionActive) {
            store.startSession(activeWindow.start.toInstant().toEpochMilli(), activeWindow.wake.toInstant().toEpochMilli())
            NativeLog.d("schedule restored after boot")
        }
        ScheduleManager(context).scheduleWake(store.sessionWake)
        ScheduleManager(context).scheduleNextBedtime()
        BedtimeEffectsController.applyCurrentState(context)
        if (store.temporaryUnlockExpiry > now) {
            ScheduleManager(context).scheduleTemporaryExpiry(store.temporaryUnlockExpiry)
            TemporaryAccessNotification.show(context, store.temporaryUnlockExpiry)
        } else if (store.temporaryUnlockExpiry != 0L) {
            expireTemporaryAccess(context)
        } else if (store.lockPending) {
            BedtimeAccessibilityService.instance?.lockAndEnforce()
        } else {
            BedtimeAccessibilityService.instance?.enforceNow()
        }
    }

    fun startBedtime(context: Context) {
        val store = BedtimeStateStore(context)
        if (!store.setupComplete || !store.scheduleEnabled) return
        val window = ScheduleCalculator.activeWindow(
            now = ZonedDateTime.now(ZoneId.systemDefault()),
            bedtimeHour = store.bedtimeHour,
            bedtimeMinute = store.bedtimeMinute,
            wakeHour = store.wakeHour,
            wakeMinute = store.wakeMinute,
        ) ?: return
        store.startSession(window.start.toInstant().toEpochMilli(), window.wake.toInstant().toEpochMilli())
        store.setLockPending(store.lockScreenEnabled)
        BedtimeEffectsController.applyCurrentState(context)
        TemporaryAccessNotification.cancel(context)
        ScheduleManager(context).apply {
            scheduleWake(window.wake.toInstant().toEpochMilli())
            scheduleNextBedtime()
        }
        BedtimeNotificationListenerService.snoozeCurrentNotifications(context)
        NativeLog.d("bedtime started")
        BedtimeAccessibilityService.instance?.lockAndEnforce()
    }

    fun endBedtime(context: Context) {
        val store = BedtimeStateStore(context)
        BedtimeEffectsController.endSession(context)
        store.endSession()
        ScheduleManager(context).apply {
            cancelWake()
            cancelTemporaryExpiry()
            scheduleNextBedtime()
        }
        TemporaryAccessNotification.cancel(context)
        BedtimeAccessibilityService.instance?.hideBlocker()
        NativeLog.d("wake triggered")
    }

    fun startTemporaryAccess(context: Context) {
        val store = BedtimeStateStore(context)
        if (!store.sessionActive) return
        val expiry = ScheduleCalculator.temporaryExpiry(System.currentTimeMillis(), store.sessionWake)
        if (expiry <= System.currentTimeMillis()) {
            endBedtime(context)
            return
        }
        store.setTemporaryUnlockExpiry(expiry)
        BedtimeEffectsController.applyCurrentState(context)
        ScheduleManager(context).scheduleTemporaryExpiry(expiry)
        TemporaryAccessNotification.show(context, expiry)
        BedtimeAccessibilityService.instance?.hideBlocker()
        NativeLog.d("temporary access started")
    }

    fun expireTemporaryAccess(context: Context) {
        val store = BedtimeStateStore(context)
        if (!store.sessionActive) return
        if (store.sessionWake <= System.currentTimeMillis()) {
            endBedtime(context)
            return
        }
        store.clearTemporaryUnlock()
        BedtimeEffectsController.applyCurrentState(context)
        ScheduleManager(context).cancelTemporaryExpiry()
        TemporaryAccessNotification.cancel(context)
        NativeLog.d("temporary access ended")
        store.setLockPending(store.lockScreenEnabled)
        BedtimeAccessibilityService.instance?.lockAndEnforce()
    }

    fun lockNow(context: Context) {
        val store = BedtimeStateStore(context)
        if (!store.sessionActive) return
        store.clearTemporaryUnlock()
        BedtimeEffectsController.applyCurrentState(context)
        ScheduleManager(context).cancelTemporaryExpiry()
        TemporaryAccessNotification.cancel(context)
        store.setLockPending(store.lockScreenEnabled)
        BedtimeAccessibilityService.instance?.lockAndEnforce()
    }
}
