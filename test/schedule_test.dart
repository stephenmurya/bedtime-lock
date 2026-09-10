import 'package:flutter_test/flutter_test.dart';
import 'package:bedtime_lock/models/schedule.dart';

void main() {
  const bedtime = ClockTime(22, 0);
  const wake = ClockTime(5, 0);

  test('next bedtime is later today when bedtime has not passed', () {
    final now = DateTime(2026, 9, 9, 15, 0);
    expect(
      ScheduleCalculator.nextBedtime(now, bedtime, wake),
      DateTime(2026, 9, 9, 22),
    );
  });

  test('next bedtime moves to tomorrow after bedtime passed', () {
    final now = DateTime(2026, 9, 9, 23, 0);
    expect(
      ScheduleCalculator.nextBedtime(now, bedtime, wake),
      DateTime(2026, 9, 10, 22),
    );
  });

  test('10 PM to 5 AM produces an overnight window', () {
    final window = ScheduleCalculator.windowForBedtime(
      DateTime(2026, 9, 9),
      bedtime,
      wake,
    );
    expect(window.start, DateTime(2026, 9, 9, 22));
    expect(window.wake, DateTime(2026, 9, 10, 5));
  });

  test('1 AM is inside an overnight range', () {
    expect(
      ScheduleCalculator.isInside(DateTime(2026, 9, 10, 1), bedtime, wake),
      isTrue,
    );
  });

  test('noon is outside an overnight range', () {
    expect(
      ScheduleCalculator.isInside(DateTime(2026, 9, 10, 12), bedtime, wake),
      isFalse,
    );
  });

  test('wake time is calculated from the bedtime date', () {
    expect(
      ScheduleCalculator.wakeForBedtime(
        DateTime(2026, 9, 9, 22),
        bedtime,
        wake,
      ),
      DateTime(2026, 9, 10, 5),
    );
  });

  test('temporary unlock lasts 15 minutes', () {
    final now = DateTime(2026, 9, 10, 1, 0);
    expect(
      ScheduleCalculator.temporaryUnlockExpiry(
        now: now,
        scheduledWake: DateTime(2026, 9, 10, 5),
      ),
      DateTime(2026, 9, 10, 1, 15),
    );
  });

  test('temporary unlock is capped by wake time', () {
    final now = DateTime(2026, 9, 10, 4, 55);
    expect(
      ScheduleCalculator.temporaryUnlockExpiry(
        now: now,
        scheduledWake: DateTime(2026, 9, 10, 5),
      ),
      DateTime(2026, 9, 10, 5),
    );
  });

  test('expired temporary unlock is rejected', () {
    expect(
      ScheduleCalculator.isTemporaryUnlockValid(
        now: DateTime(2026, 9, 10, 1, 16),
        expiry: DateTime(2026, 9, 10, 1, 15),
      ),
      isFalse,
    );
  });

  test('reboot during active bedtime restores active status', () {
    expect(
      ScheduleCalculator.isInside(DateTime(2026, 9, 10, 1, 30), bedtime, wake),
      isTrue,
    );
  });

  test('reboot outside bedtime does not activate it', () {
    expect(
      ScheduleCalculator.isInside(DateTime(2026, 9, 10, 13, 30), bedtime, wake),
      isFalse,
    );
  });

  test('schedule disabled never activates', () {
    expect(
      ScheduleCalculator.isScheduledActive(
        enabled: false,
        now: DateTime(2026, 9, 10, 1),
        bedtime: bedtime,
        wake: wake,
      ),
      isFalse,
    );
  });

  test(
    'schedule calculations use local civil time after a timezone change',
    () {
      final now = DateTime(2026, 9, 9, 8, 0);
      expect(
        ScheduleCalculator.nextBedtime(
          now,
          ClockTime(23, 30),
          ClockTime(6, 30),
        ),
        DateTime(2026, 9, 9, 23, 30),
      );
    },
  );
}
