package com.omeniv.bedtimelock

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ScheduleWindow(val start: ZonedDateTime, val wake: ZonedDateTime)

object ScheduleCalculator {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun windowForBedtime(
        date: LocalDate,
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
    ): ScheduleWindow {
        val startLocal = LocalDateTime.of(date, LocalTime.of(bedtimeHour, bedtimeMinute))
        val wakeDate = if (bedtimeHour * 60 + bedtimeMinute >= wakeHour * 60 + wakeMinute) date.plusDays(1) else date
        val wakeLocal = LocalDateTime.of(wakeDate, LocalTime.of(wakeHour, wakeMinute))
        return ScheduleWindow(startLocal.atZone(zone), wakeLocal.atZone(zone))
    }

    fun activeWindow(
        now: ZonedDateTime = ZonedDateTime.now(zone),
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
    ): ScheduleWindow? {
        val today = windowForBedtime(now.toLocalDate(), bedtimeHour, bedtimeMinute, wakeHour, wakeMinute)
        if (!now.isBefore(today.start) && now.isBefore(today.wake)) return today
        val yesterday = windowForBedtime(now.toLocalDate().minusDays(1), bedtimeHour, bedtimeMinute, wakeHour, wakeMinute)
        return if (!now.isBefore(yesterday.start) && now.isBefore(yesterday.wake)) yesterday else null
    }

    fun nextBedtime(
        now: ZonedDateTime = ZonedDateTime.now(zone),
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
    ): ZonedDateTime {
        if (activeWindow(now, bedtimeHour, bedtimeMinute, wakeHour, wakeMinute) != null) {
            return windowForBedtime(now.toLocalDate().plusDays(1), bedtimeHour, bedtimeMinute, wakeHour, wakeMinute).start
        }
        val candidate = windowForBedtime(now.toLocalDate(), bedtimeHour, bedtimeMinute, wakeHour, wakeMinute).start
        return if (candidate.isAfter(now)) candidate else windowForBedtime(now.toLocalDate().plusDays(1), bedtimeHour, bedtimeMinute, wakeHour, wakeMinute).start
    }

    fun temporaryExpiry(now: Long, wake: Long): Long = minOf(now + 15 * 60_000L, wake)
}
