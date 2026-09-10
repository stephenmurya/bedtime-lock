class ClockTime {
  final int hour;
  final int minute;

  const ClockTime(this.hour, this.minute)
    : assert(hour >= 0 && hour < 24),
      assert(minute >= 0 && minute < 60);

  int get minutesSinceMidnight => hour * 60 + minute;

  ClockTime copyWith({int? hour, int? minute}) =>
      ClockTime(hour ?? this.hour, minute ?? this.minute);

  @override
  bool operator ==(Object other) =>
      other is ClockTime && hour == other.hour && minute == other.minute;

  @override
  int get hashCode => Object.hash(hour, minute);
}

class BedtimeWindow {
  final DateTime start;
  final DateTime wake;

  const BedtimeWindow(this.start, this.wake);
}

class ScheduleCalculator {
  static BedtimeWindow windowForBedtime(
    DateTime bedtimeDate,
    ClockTime bedtime,
    ClockTime wake,
  ) {
    final start = DateTime(
      bedtimeDate.year,
      bedtimeDate.month,
      bedtimeDate.day,
      bedtime.hour,
      bedtime.minute,
    );
    final wakeIsNextDay =
        bedtime.minutesSinceMidnight >= wake.minutesSinceMidnight;
    final wakeDate = wakeIsNextDay ? start.add(const Duration(days: 1)) : start;
    return BedtimeWindow(
      start,
      DateTime(
        wakeDate.year,
        wakeDate.month,
        wakeDate.day,
        wake.hour,
        wake.minute,
      ),
    );
  }

  static BedtimeWindow? activeWindow(
    DateTime now,
    ClockTime bedtime,
    ClockTime wake,
  ) {
    final today = windowForBedtime(now, bedtime, wake);
    if (!now.isBefore(today.start) && now.isBefore(today.wake)) return today;

    final yesterday = windowForBedtime(
      now.subtract(const Duration(days: 1)),
      bedtime,
      wake,
    );
    if (!now.isBefore(yesterday.start) && now.isBefore(yesterday.wake)) {
      return yesterday;
    }
    return null;
  }

  static bool isInside(DateTime now, ClockTime bedtime, ClockTime wake) =>
      activeWindow(now, bedtime, wake) != null;

  static bool isScheduledActive({
    required bool enabled,
    required DateTime now,
    required ClockTime bedtime,
    required ClockTime wake,
  }) => enabled && isInside(now, bedtime, wake);

  static DateTime nextBedtime(DateTime now, ClockTime bedtime, ClockTime wake) {
    final current = activeWindow(now, bedtime, wake);
    if (current != null) {
      return windowForBedtime(
        now.add(const Duration(days: 1)),
        bedtime,
        wake,
      ).start;
    }

    final candidate = windowForBedtime(now, bedtime, wake).start;
    return candidate.isAfter(now)
        ? candidate
        : windowForBedtime(
            now.add(const Duration(days: 1)),
            bedtime,
            wake,
          ).start;
  }

  static DateTime wakeForBedtime(
    DateTime bedtimeStart,
    ClockTime bedtime,
    ClockTime wake,
  ) => windowForBedtime(bedtimeStart, bedtime, wake).wake;

  static DateTime temporaryUnlockExpiry({
    required DateTime now,
    required DateTime scheduledWake,
  }) {
    final requested = now.add(const Duration(minutes: 15));
    return requested.isBefore(scheduledWake) ? requested : scheduledWake;
  }

  static bool isTemporaryUnlockValid({
    required DateTime now,
    required DateTime? expiry,
  }) => expiry != null && now.isBefore(expiry);
}
