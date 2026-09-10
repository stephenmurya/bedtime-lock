# Bedtime Lock

### A quiet Android boundary for scheduled sleep hours

Bedtime Lock is an Omen IV utility for people who want their phone to become
less distracting at night. Choose a bedtime and wake time, keep essential
phone functions available, and use an accountability partner when temporary
access is genuinely needed.

## Overview

At bedtime, Bedtime Lock uses Android's Accessibility Service and a native
full-screen blocker to discourage normal app use. The schedule survives app
restarts and device reboots. Phone and Clock remain available, while normal
notifications can be held until wake time.

## Core features

- Scheduled bedtime and wake time, including overnight schedules.
- Native blocker overlay over non-allowed apps.
- Accessibility-based enforcement with Phone and Clock availability.
- Salted PBKDF2-HMAC-SHA256 accountability partner PIN.
- Fifteen-minute temporary unlock, capped by wake time.
- Persistent countdown notification with a single Lock now action.
- Notification snoozing until the stored wake time, without deleting alerts.
- Do Not Disturb, optional screen dimming, and optional screen lock effects.
- Reboot and timezone recovery for an active bedtime session.

## Permissions and why they are needed

Bedtime Lock asks for the following Android capabilities during setup:

| Capability | What it enables |
| --- | --- |
| Accessibility | Detects the foreground app and keeps the bedtime boundary enforced. |
| Appear on top | Places the native blocker above other apps. |
| Precise alarms | Starts bedtime and ends it at the configured times. |
| Boot completed | Restores schedules after a device restart. |
| Notifications | Shows the temporary-access countdown and its Lock now action. |
| Notification access | Snoozes ordinary notifications until wake time. |
| Notification policy access | Applies the app-owned bedtime Do Not Disturb rule. |
| Display control | Saves and restores brightness when Dim screen is enabled. |
| Battery optimization exemption | Helps alarms and enforcement remain reliable while the app is closed. |

The app does not need an internet connection for its core behavior.

## Accountability and temporary access

The accountability partner PIN is stored as a salted password hash. The clear
PIN is never persisted or logged. A correct PIN starts one temporary unlock for
up to fifteen minutes, or until wake time if that comes first.

During temporary access, the blocker is hidden, bedtime DND is paused, the
original brightness is restored, and new ordinary notifications may appear.
Notifications already held for bedtime remain held until wake. The countdown
notification stays visible and its Lock now action immediately reapplies the
bedtime boundary.

## Notification behavior

When Hide notifications is enabled and notification access is granted, normal
third-party notifications are snoozed until the active session's wake time.
They are not permanently cancelled. Ongoing, foreground-service, alarm, call,
system, and Bedtime Lock notifications may remain visible because Android or
the originating app treats them as persistent or critical.

## Limitations

Bedtime Lock is a user-space enforcement utility, not Device Owner or kiosk
mode. It is designed to strongly discourage normal use, not provide an
OS-level tamper-proof lockdown. Android and OEM behavior can vary slightly,
and some persistent or system notifications may remain visible. Hardware
reboot, uninstall, disabling Accessibility, and every system surface remain
outside an ordinary app's guarantee.

## Package and version

- App name: Bedtime Lock
- Brand: Omen IV utility
- Package: `com.omeniv.bedtimelock`
- Version: `0.0.3` (version code `3`)

## Release artifact

The branded release APK is produced at:

`build/app/outputs/flutter-apk/app-release.apk`
