package com.omeniv.bedtimelock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent

class BedtimeAccessibilityService : AccessibilityService() {
    private lateinit var store: BedtimeStateStore
    private lateinit var overlay: OverlayController
    private lateinit var allowedApps: AllowedAppsResolver
    private var lastPackageName: String? = null
    private var lastWindowId: Int = -1
    private var lastClassName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = BedtimeStateStore(this)
        overlay = OverlayController(this)
        allowedApps = AllowedAppsResolver(this)
        instance = this
        NativeLog.d("accessibility connected")
        val currentStore = BedtimeStateStore(this)
        if (currentStore.sessionActive && currentStore.temporaryUnlockExpiry == 0L && currentStore.lockPending) {
            lockAndEnforce()
        } else {
            enforceNow()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return
        val className = event.className?.toString()
        if (packageName == lastPackageName && event.windowId == lastWindowId && className == lastClassName) return
        lastPackageName = packageName
        lastWindowId = event.windowId
        lastClassName = className
        enforceNow(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        overlay.destroy()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun enforceNow(packageName: String? = null) {
        val currentStore = BedtimeStateStore(this)
        if (!currentStore.sessionActive) {
            overlay.hide()
            return
        }
        if (currentStore.sessionWake <= System.currentTimeMillis()) {
            BedtimeCoordinator.endBedtime(this)
            return
        }
        if (currentStore.temporaryUnlockExpiry != 0L) {
            if (currentStore.temporaryUnlockExpiry <= System.currentTimeMillis()) {
                BedtimeCoordinator.expireTemporaryAccess(this)
            } else {
                overlay.hide()
            }
            return
        }
        if (packageName != null && allowedApps.isAllowed(packageName)) {
            overlay.hide()
        } else {
            overlay.ensureVisible()
        }
    }

    fun lockAndEnforce(forceLock: Boolean = false) {
        overlay.ensureVisible()
        val currentStore = BedtimeStateStore(this)
        if (forceLock || (currentStore.lockScreenEnabled && currentStore.lockPending)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val locked = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                NativeLog.d(if (locked) "screen locked" else "screen lock action rejected")
            } else {
                NativeLog.d("screen lock unavailable on this Android version")
            }
            currentStore.setLockPending(false)
        }
    }

    fun hideBlocker() = overlay.hide()

    fun handlePin(pin: String) {
        val result = BedtimeStateStore(this).verifyPin(pin)
        if (result.verified) {
            BedtimeCoordinator.startTemporaryAccess(this)
            overlay.hide()
        } else {
            overlay.showMain()
        }
    }

    fun callPartner() {
        val phone = BedtimeStateStore(this).partnerPhone
        if (phone.isBlank()) return
        overlay.hide()
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    companion object {
        @Volatile
        var instance: BedtimeAccessibilityService? = null
            private set
    }
}
