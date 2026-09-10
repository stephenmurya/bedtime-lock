package com.omeniv.bedtimelock

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class BedtimeStateStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val scheduleEnabled: Boolean get() = preferences.getBoolean(KEY_SCHEDULE_ENABLED, false)
    val setupComplete: Boolean get() = preferences.getBoolean(KEY_SETUP_COMPLETE, false)
    val bedtimeHour: Int get() = preferences.getInt(KEY_BEDTIME_HOUR, 22)
    val bedtimeMinute: Int get() = preferences.getInt(KEY_BEDTIME_MINUTE, 0)
    val wakeHour: Int get() = preferences.getInt(KEY_WAKE_HOUR, 5)
    val wakeMinute: Int get() = preferences.getInt(KEY_WAKE_MINUTE, 0)
    val partnerName: String get() = preferences.getString(KEY_PARTNER_NAME, "Partner") ?: "Partner"
    val partnerPhone: String get() = preferences.getString(KEY_PARTNER_PHONE, "") ?: ""
    val sessionActive: Boolean get() = preferences.getBoolean(KEY_SESSION_ACTIVE, false)
    val sessionStart: Long get() = preferences.getLong(KEY_SESSION_START, 0L)
    val sessionWake: Long get() = preferences.getLong(KEY_SESSION_WAKE, 0L)
    val temporaryUnlockExpiry: Long get() = preferences.getLong(KEY_TEMPORARY_EXPIRY, 0L)
    val doNotDisturbEnabled: Boolean get() = preferences.getBoolean(KEY_DND_ENABLED, true)
    val hideNotificationsEnabled: Boolean get() = preferences.getBoolean(KEY_HIDE_NOTIFICATIONS, true)
    val dimScreenEnabled: Boolean get() = preferences.getBoolean(KEY_DIM_SCREEN, true)
    val bedtimeBrightnessPercent: Int get() = preferences.getInt(KEY_BEDTIME_BRIGHTNESS, 10)
    val lockScreenEnabled: Boolean get() = preferences.getBoolean(KEY_LOCK_SCREEN, true)
    val originalBrightnessSaved: Boolean get() = preferences.getBoolean(KEY_ORIGINAL_BRIGHTNESS_SAVED, false)
    val originalBrightness: Int get() = preferences.getInt(KEY_ORIGINAL_BRIGHTNESS, 128)
    val originalBrightnessMode: Int get() = preferences.getInt(KEY_ORIGINAL_BRIGHTNESS_MODE, 1)
    val notificationSweepWake: Long get() = preferences.getLong(KEY_NOTIFICATION_SWEEP_WAKE, 0L)
    val lockPending: Boolean get() = preferences.getBoolean(KEY_LOCK_PENDING, false)
    val dndRuleId: String? get() = preferences.getString(KEY_DND_RULE_ID, null)

    fun saveConfiguration(
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        partnerName: String,
        partnerPhone: String,
        pin: String,
    ) {
        val editor = preferences.edit()
            .putInt(KEY_BEDTIME_HOUR, bedtimeHour)
            .putInt(KEY_BEDTIME_MINUTE, bedtimeMinute)
            .putInt(KEY_WAKE_HOUR, wakeHour)
            .putInt(KEY_WAKE_MINUTE, wakeMinute)
            .putString(KEY_PARTNER_NAME, partnerName)
            .putString(KEY_PARTNER_PHONE, partnerPhone)
            .putBoolean(KEY_SCHEDULE_ENABLED, true)
            .putBoolean(KEY_SETUP_COMPLETE, true)
        setPin(editor, pin)
        editor.apply()
    }

    fun setSchedule(bedtimeHour: Int, bedtimeMinute: Int, wakeHour: Int, wakeMinute: Int) {
        preferences.edit()
            .putInt(KEY_BEDTIME_HOUR, bedtimeHour)
            .putInt(KEY_BEDTIME_MINUTE, bedtimeMinute)
            .putInt(KEY_WAKE_HOUR, wakeHour)
            .putInt(KEY_WAKE_MINUTE, wakeMinute)
            .apply()
    }

    fun setScheduleEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
    }

    fun setEffects(
        doNotDisturb: Boolean,
        hideNotifications: Boolean,
        dimScreen: Boolean,
        bedtimeBrightnessPercent: Int,
        lockScreen: Boolean,
    ) {
        preferences.edit()
            .putBoolean(KEY_DND_ENABLED, doNotDisturb)
            .putBoolean(KEY_HIDE_NOTIFICATIONS, hideNotifications)
            .putBoolean(KEY_DIM_SCREEN, dimScreen)
            .putInt(KEY_BEDTIME_BRIGHTNESS, bedtimeBrightnessPercent.coerceIn(1, 100))
            .putBoolean(KEY_LOCK_SCREEN, lockScreen)
            .apply()
    }

    fun updatePartner(name: String, phone: String, pin: String?) {
        val editor = preferences.edit()
            .putString(KEY_PARTNER_NAME, name)
            .putString(KEY_PARTNER_PHONE, phone)
        if (!pin.isNullOrBlank()) setPin(editor, pin)
        editor.apply()
    }

    fun startSession(start: Long, wake: Long) {
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putLong(KEY_SESSION_START, start)
            .putLong(KEY_SESSION_WAKE, wake)
            .putLong(KEY_TEMPORARY_EXPIRY, 0L)
            .putBoolean(KEY_ORIGINAL_BRIGHTNESS_SAVED, false)
            .putLong(KEY_NOTIFICATION_SWEEP_WAKE, 0L)
            .putBoolean(KEY_LOCK_PENDING, false)
            .apply()
    }

    fun endSession() {
        preferences.edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .putLong(KEY_SESSION_START, 0L)
            .putLong(KEY_SESSION_WAKE, 0L)
            .putLong(KEY_TEMPORARY_EXPIRY, 0L)
            .putBoolean(KEY_ORIGINAL_BRIGHTNESS_SAVED, false)
            .putLong(KEY_NOTIFICATION_SWEEP_WAKE, 0L)
            .putBoolean(KEY_LOCK_PENDING, false)
            .apply()
    }

    fun setTemporaryUnlockExpiry(expiry: Long) {
        preferences.edit().putLong(KEY_TEMPORARY_EXPIRY, expiry).apply()
    }

    fun clearTemporaryUnlock() {
        preferences.edit().putLong(KEY_TEMPORARY_EXPIRY, 0L).apply()
    }

    fun saveOriginalBrightness(value: Int, mode: Int) {
        if (originalBrightnessSaved) return
        preferences.edit()
            .putInt(KEY_ORIGINAL_BRIGHTNESS, value)
            .putInt(KEY_ORIGINAL_BRIGHTNESS_MODE, mode)
            .putBoolean(KEY_ORIGINAL_BRIGHTNESS_SAVED, true)
            .apply()
    }

    fun clearOriginalBrightness() {
        preferences.edit().putBoolean(KEY_ORIGINAL_BRIGHTNESS_SAVED, false).apply()
    }

    fun markNotificationSweep(wake: Long) {
        preferences.edit().putLong(KEY_NOTIFICATION_SWEEP_WAKE, wake).apply()
    }

    fun clearNotificationSweep() {
        preferences.edit().putLong(KEY_NOTIFICATION_SWEEP_WAKE, 0L).apply()
    }

    fun setLockPending(pending: Boolean) {
        preferences.edit().putBoolean(KEY_LOCK_PENDING, pending).apply()
    }

    fun setDndRuleId(id: String) {
        preferences.edit().putString(KEY_DND_RULE_ID, id).apply()
    }

    fun verifyPin(pin: String): PinVerification {
        val now = System.currentTimeMillis()
        val cooldownUntil = preferences.getLong(KEY_PIN_COOLDOWN_UNTIL, 0L)
        if (cooldownUntil > now) {
            val seconds = ((cooldownUntil - now + 999L) / 1000L).toInt()
            return PinVerification(false, "Too many attempts. Try again in ${seconds.coerceAtLeast(1)} seconds.")
        }

        val salt = preferences.getString(KEY_PIN_SALT, null)
        val hash = preferences.getString(KEY_PIN_HASH, null)
        if (salt.isNullOrBlank() || hash.isNullOrBlank()) return PinVerification(false, "PIN is not configured.")

        val enteredHash = derive(pin, Base64.decode(salt, Base64.NO_WRAP))
        if (MessageDigest.isEqual(enteredHash, Base64.decode(hash, Base64.NO_WRAP))) {
            preferences.edit().putInt(KEY_PIN_ATTEMPTS, 0).apply()
            return PinVerification(true, null)
        }

        val attempts = preferences.getInt(KEY_PIN_ATTEMPTS, 0) + 1
        val editor = preferences.edit()
        if (attempts >= MAX_PIN_ATTEMPTS) {
            editor.putInt(KEY_PIN_ATTEMPTS, 0).putLong(KEY_PIN_COOLDOWN_UNTIL, now + PIN_COOLDOWN_MS)
        } else {
            editor.putInt(KEY_PIN_ATTEMPTS, attempts)
        }
        editor.apply()
        return PinVerification(false, "That PIN was not accepted.")
    }

    fun asMap(): Map<String, Any?> = mapOf(
        "setupComplete" to setupComplete,
        "scheduleEnabled" to scheduleEnabled,
        "bedtimeHour" to bedtimeHour,
        "bedtimeMinute" to bedtimeMinute,
        "wakeHour" to wakeHour,
        "wakeMinute" to wakeMinute,
        "partnerName" to partnerName,
        "partnerPhone" to partnerPhone,
        "sessionActive" to sessionActive,
        "sessionStart" to sessionStart,
        "sessionWake" to sessionWake,
        "temporaryUnlockExpiry" to temporaryUnlockExpiry,
        "doNotDisturbEnabled" to doNotDisturbEnabled,
        "hideNotificationsEnabled" to hideNotificationsEnabled,
        "dimScreenEnabled" to dimScreenEnabled,
        "bedtimeBrightnessPercent" to bedtimeBrightnessPercent,
        "lockScreenEnabled" to lockScreenEnabled,
    )

    private fun setPin(editor: SharedPreferences.Editor, pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        editor.putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_PIN_ATTEMPTS, 0)
            .putLong(KEY_PIN_COOLDOWN_UNTIL, 0L)
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val specification = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded
        } finally {
            specification.clearPassword()
        }
    }

    data class PinVerification(val verified: Boolean, val message: String?)

    companion object {
        private const val FILE_NAME = "bedtime_lock_state"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_BEDTIME_HOUR = "bedtime_hour"
        private const val KEY_BEDTIME_MINUTE = "bedtime_minute"
        private const val KEY_WAKE_HOUR = "wake_hour"
        private const val KEY_WAKE_MINUTE = "wake_minute"
        private const val KEY_PARTNER_NAME = "partner_name"
        private const val KEY_PARTNER_PHONE = "partner_phone"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_ATTEMPTS = "pin_attempts"
        private const val KEY_PIN_COOLDOWN_UNTIL = "pin_cooldown_until"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_START = "session_start"
        private const val KEY_SESSION_WAKE = "session_wake"
        private const val KEY_TEMPORARY_EXPIRY = "temporary_unlock_expiry"
        private const val KEY_DND_ENABLED = "do_not_disturb_enabled"
        private const val KEY_HIDE_NOTIFICATIONS = "hide_notifications_enabled"
        private const val KEY_DIM_SCREEN = "dim_screen_enabled"
        private const val KEY_BEDTIME_BRIGHTNESS = "bedtime_brightness_percent"
        private const val KEY_LOCK_SCREEN = "lock_screen_enabled"
        private const val KEY_ORIGINAL_BRIGHTNESS_SAVED = "original_brightness_saved"
        private const val KEY_ORIGINAL_BRIGHTNESS = "original_brightness"
        private const val KEY_ORIGINAL_BRIGHTNESS_MODE = "original_brightness_mode"
        private const val KEY_NOTIFICATION_SWEEP_WAKE = "notification_sweep_wake"
        private const val KEY_LOCK_PENDING = "lock_pending"
        private const val KEY_DND_RULE_ID = "dnd_rule_id"
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val PBKDF2_ITERATIONS = 150_000
        private const val MAX_PIN_ATTEMPTS = 5
        private const val PIN_COOLDOWN_MS = 60_000L
    }
}
