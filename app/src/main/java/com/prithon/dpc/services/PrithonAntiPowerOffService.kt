package com.prithon.dpc.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.ui.LockActivity

/**
 * High-Security Anti-Power-Off & Anti-Tamper Accessibility Engine.
 *
 * When the phone is in LOCKED state, this service intercepts:
 * 1. Global Actions / Power Off / Restart dialogs from SystemUI and slams them shut in <15ms.
 * 2. Settings app launches (preventing Force Stop or Uninstall attempts).
 * 3. Re-asserts LockActivity immediately to keep screen pinned.
 */
class PrithonAntiPowerOffService : AccessibilityService() {

    companion object {
        private const val TAG = "AntiPowerOffService"
        @Volatile
        var isServiceActive: Boolean = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceActive = true
        Log.i(TAG, "Prithon Anti-Power-Off Accessibility Engine CONNECTED.")

        val config = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 10
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = config
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Anti-tamper enforcement only active when device is locked
        val isLocked = DeviceLockManager.isKioskLocked || DeviceStateManager.isDeviceLocked(this)
        if (!isLocked) return

        val pkgName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        val textContent = event.text?.joinToString(" ") { it.toString() } ?: ""

        // 1. Check for Power Menu / Global Actions / Restart Dialog
        val isPowerMenuTriggered = pkgName == "com.android.systemui" && (
            className.contains("GlobalActions", ignoreCase = true) ||
            className.contains("Power", ignoreCase = true) ||
            className.contains("Shutdown", ignoreCase = true) ||
            className.contains("Restart", ignoreCase = true) ||
            textContent.contains("Power off", ignoreCase = true) ||
            textContent.contains("Restart", ignoreCase = true) ||
            textContent.contains("Reboot", ignoreCase = true) ||
            textContent.contains("Switch off", ignoreCase = true) ||
            textContent.contains("Turn off", ignoreCase = true) ||
            textContent.contains("Emergency", ignoreCase = true) ||
            textContent.contains("बंद करें", ignoreCase = true) ||
            textContent.contains("रीस्टार्ट", ignoreCase = true) ||
            textContent.contains("Power", ignoreCase = true)
        )

        if (isPowerMenuTriggered) {
            Log.w(TAG, "⚡ POWER MENU DETECTED! Dismissing dialog instantly via GLOBAL_ACTION_BACK!")
            performGlobalAction(GLOBAL_ACTION_BACK)
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_BACK)
                relaunchLockActivity()
            }, 30)
            return
        }

        // 2. Anti-Tamper: Block Settings, App Manager, or Package Installer while locked
        val isSettingsAttempt = pkgName == "com.android.settings" ||
                pkgName == "com.google.android.packageinstaller" ||
                pkgName == "com.android.packageinstaller"

        if (isSettingsAttempt) {
            Log.w(TAG, "⚡ TAMPER DETECTED: Defaulter attempted to access $pkgName while locked! Snatched back!")
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            relaunchLockActivity()
        }
    }

    private fun relaunchLockActivity() {
        try {
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(lockIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error relaunching LockActivity: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Prithon Anti-Power-Off Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        Log.w(TAG, "Prithon Anti-Power-Off Service destroyed.")
    }
}
