package com.omeniv.bedtimelock

object NotificationSnoozeCalculator {
    fun shouldSnooze(
        sessionActive: Boolean,
        hideNotifications: Boolean,
        wake: Long,
        temporaryUnlockExpiry: Long,
        now: Long,
    ): Boolean = sessionActive && hideNotifications && wake > now && temporaryUnlockExpiry <= now

    fun remainingUntilWake(wake: Long, now: Long): Long = (wake - now).coerceAtLeast(1_000L)
}
