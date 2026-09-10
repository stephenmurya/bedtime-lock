package com.omeniv.bedtimelock

import android.app.Notification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {
    private val ownPackage = "com.omeniv.bedtimelock"

    @Test
    fun ordinaryUserNotificationIsEligible() {
        assertTrue(decision(flags = 0, category = null).eligible)
    }

    @Test
    fun ongoingForegroundAndSystemNotificationsAreExempt() {
        assertFalse(decision(flags = Notification.FLAG_ONGOING_EVENT, category = null).eligible)
        assertFalse(decision(flags = Notification.FLAG_FOREGROUND_SERVICE, category = null).eligible)
        assertFalse(NotificationFilter.decision(metadata(flags = 0, category = null, systemApp = true), ownPackage).eligible)
    }

    @Test
    fun ownAlarmAndCallNotificationsAreExempt() {
        assertFalse(decision(flags = 0, category = Notification.CATEGORY_ALARM).eligible)
        assertFalse(decision(flags = 0, category = Notification.CATEGORY_CALL).eligible)
        assertFalse(
            NotificationFilter.decision(
                metadata(packageName = ownPackage, flags = 0, category = null, systemApp = false),
                ownPackage,
            ).eligible,
        )
    }

    @Test
    fun snoozeDurationUsesStoredWakeTime() {
        assertEquals(13_620_000L, NotificationSnoozeCalculator.remainingUntilWake(5 * 60 * 60 * 1000L, 1 * 60 * 60 * 1000L + 13 * 60 * 1000L))
    }

    @Test
    fun temporaryAccessStopsNewNotificationSnoozing() {
        assertFalse(NotificationSnoozeCalculator.shouldSnooze(true, true, 10_000L, 7_000L, 6_000L))
        assertTrue(NotificationSnoozeCalculator.shouldSnooze(true, true, 10_000L, 0L, 6_000L))
        assertFalse(NotificationSnoozeCalculator.shouldSnooze(false, true, 10_000L, 0L, 6_000L))
        assertFalse(NotificationSnoozeCalculator.shouldSnooze(true, false, 10_000L, 0L, 6_000L))
    }

    @Test
    fun expiryResumesHidingAndWakeStopsIt() {
        assertTrue(NotificationSnoozeCalculator.shouldSnooze(true, true, 10_000L, 6_000L, 6_000L))
        assertFalse(NotificationSnoozeCalculator.shouldSnooze(true, true, 6_000L, 0L, 6_000L))
    }

    private fun decision(flags: Int, category: String?) =
        NotificationFilter.decision(metadata(flags = flags, category = category), ownPackage)

    private fun metadata(
        packageName: String = "com.example.chat",
        flags: Int,
        category: String?,
        systemApp: Boolean = false,
    ) = NotificationFilter.Metadata(packageName, flags, category, systemApp)
}
