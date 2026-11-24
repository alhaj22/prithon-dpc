package com.prithon.dpc.manager

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.prithon.dpc.R
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.services.MdmForegroundService
import com.prithon.dpc.ui.LockActivity

class DeviceLockManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = PrithonAdminReceiver.getComponentName(context)

    companion object {
        private const val TAG = "DeviceLockManager"
        private const val LOCK_CHANNEL_ID = "prithon_lock_channel"
        private const val LOCK_NOTIFICATION_ID = 9999
        private const val OFFLINE_MASTER_PIN = "998877"
        @Volatile
        var isKioskLocked: Boolean = false
        private var overlayView: View? = null
    }

    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    fun isDeviceAdmin(): Boolean {
        return try {
            dpm.isAdminActive(admin)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes Kiosk Lock across all Android privilege tiers:
     * 1. Screen WakeLock to ensure screen stays active & visible
     * 2. Device Owner Kiosk Pinning & Anti-Power-Off suppression (LOCK_TASK_FEATURE_NONE)
     * 3. Instant System Overlay View on WindowManager (draws over ANY running app edge-to-edge)
     * 4. Direct Fullscreen LockActivity Launch
     * 5. High-Priority FullScreen Notification Intent
     * 6. Direct Boot & CE persistence via DeviceStateManager
     */
    fun lockDevice() {
        isKioskLocked = true
        DeviceStateManager.setDeviceStatus(context, "LOCKED")
        val isOwner = isDeviceOwner()
        val isAdmin = isDeviceAdmin()
        Log.i(TAG, "Initiating direct lockDevice... isDeviceOwner=$isOwner, isDeviceAdmin=$isAdmin")

        // 1. Wake the display so lock screen is visible immediately
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Prithon:LockWakeLock"
            )
            wakeLock.acquire(10000)
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock warning: ${e.message}")
        }

        // 2. Hardware-level policies if Device Owner (Block Power Off, Safe Boot, Factory Reset)
        if (isOwner) {
            try {
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
                dpm.setStatusBarDisabled(admin, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                    )
                }
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_FACTORY_RESET)
                Log.i(TAG, "Device Owner hardware lock policies applied: Power Menu Disabled, Safe Boot Blocked, Factory Reset Blocked.")
            } catch (e: Exception) {
                Log.w(TAG, "Device Owner policy warning: ${e.message}")
            }
        }

        // 3. Show System Overlay Window on Main UI Thread (Overlays over ANY app instantly)
        Handler(Looper.getMainLooper()).post {
            showSystemOverlay()
        }

        // 4. Launch Kiosk Lock Activity directly
        try {
            val lockIntent = Intent(context, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(lockIntent)
            Log.i(TAG, "startActivity(LockActivity) invoked directly.")
        } catch (e: Exception) {
            Log.e(TAG, "startActivity failed, falling back to full-screen intent", e)
        }

        // 5. Fallback Fullscreen Intent via High-Priority Emergency Notification
        launchFullScreenLockNotification()
    }

    private fun showSystemOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW not granted")
            return
        }

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (overlayView != null) {
                try {
                    wm.removeView(overlayView)
                } catch (e: Exception) {
                    // Ignore
                }
                overlayView = null
            }

            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.activity_lock, null)

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.FILL
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            // Wire up overlay unlock button
            val etPin = view.findViewById<EditText>(R.id.etUnlockPin)
            val btnUnlock = view.findViewById<Button>(R.id.btnUnlock)
            val btnEmergency = view.findViewById<Button>(R.id.btnEmergency)
            val btnSupport = view.findViewById<Button>(R.id.btnSupport)

            btnUnlock?.setOnClickListener {
                val pin = etPin?.text?.toString()?.trim() ?: ""
                if (pin == OFFLINE_MASTER_PIN) {
                    Toast.makeText(context, "Master PIN Accepted. Unlocking...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(MdmForegroundService.ACTION_PIN_UNLOCKED).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)
                    unlockDevice()
                } else {
                    Toast.makeText(context, "Invalid PIN! Please contact your retailer.", Toast.LENGTH_SHORT).show()
                }
            }

            btnEmergency?.setOnClickListener {
                val emergencyIntent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:112")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(emergencyIntent)
            }

            btnSupport?.setOnClickListener {
                val supportIntent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:1800123456")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(supportIntent)
            }

            wm.addView(view, params)
            overlayView = view
            Log.i(TAG, "System Overlay Lock View added successfully to WindowManager.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add system overlay view", e)
        }
    }

    private fun launchFullScreenLockNotification() {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    LOCK_CHANNEL_ID,
                    "Finance Lock Emergency Enforcer",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setBypassDnd(true)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            val lockIntent = Intent(context, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val builder = NotificationCompat.Builder(context, LOCK_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Finance Protection Active")
                .setContentText("Device is locked due to overdue EMI.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setOngoing(true)
                .setAutoCancel(false)

            notificationManager.notify(LOCK_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch full screen notification", e)
        }
    }

    /**
     * Releases device from Kiosk Lock back to normal state
     */
    fun unlockDevice(lockActivity: Activity? = null) {
        isKioskLocked = false
        DeviceStateManager.setDeviceStatus(context, "NORMAL")
        Log.i(TAG, "Initiating unlockDevice...")

        // 1. Remove System Overlay View on Main Thread
        Handler(Looper.getMainLooper()).post {
            try {
                if (overlayView != null) {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    wm.removeView(overlayView)
                    overlayView = null
                    Log.i(TAG, "System overlay view removed.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view: ${e.message}")
            }
        }

        // 2. Dismiss full-screen lock notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(LOCK_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Release Device Owner policies
        if (isDeviceOwner()) {
            try {
                dpm.setLockTaskPackages(admin, arrayOf())
                dpm.setStatusBarDisabled(admin, false)
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_FACTORY_RESET)
            } catch (e: Exception) {
                Log.w(TAG, "Device Owner release policy warning: ${e.message}")
            }
        }

        try {
            lockActivity?.stopLockTask()
        } catch (e: Exception) {
            // Ignore
        }

        lockActivity?.finishAndRemoveTask()
        Log.i(TAG, "Device successfully RELEASED to NORMAL mode.")
    }

    /**
     * Permanent Enterprise Device Release & Self-Deletion:
     * Triggered when Admin clicks "Delete Device" on Dashboard:
     * 1. Unlocks screen and dismisses all kiosk overlays
     * 2. Clears all enterprise hardware restrictions and policies
     * 3. Wipes all locally saved tenant/customer finance credentials
     * 4. Revokes Device Administrator and Device Owner privileges
     * 5. Prompts automatic uninstallation of the application
     */
    fun releaseAndUninstall(activity: Activity? = null) {
        Log.w(TAG, "🚨 CRITICAL: Remote RELEASE_AND_UNINSTALL command received from Admin Dashboard!")

        // 1. Release Lock & Overlay
        unlockDevice(activity)

        // 2. Clear all local SharedPreferences and Direct Boot credentials
        try {
            context.getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("prithon_location_prefs", Context.MODE_PRIVATE).edit().clear().apply()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    context.createDeviceProtectedStorageContext()
                        .getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                } catch (e: Exception) {
                    // Ignore
                }
            }
            Log.i(TAG, "All local finance KYC credentials wiped.")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local preferences: ${e.message}")
        }

        // 3. Remove Device Owner privileges FIRST, then Device Administrator
        try {
            if (isDeviceOwner()) {
                try {
                    dpm.setLockTaskPackages(admin, arrayOf())
                    dpm.setStatusBarDisabled(admin, false)
                    dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
                    dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_FACTORY_RESET)
                    dpm.clearDeviceOwnerApp(context.packageName)
                    Log.i(TAG, "Device Owner successfully cleared.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error clearing Device Owner: ${e.message}")
                }
            }

            if (dpm.isAdminActive(admin)) {
                try {
                    dpm.removeActiveAdmin(admin)
                    Log.i(TAG, "Device Administrator successfully revoked.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error revoking active admin: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking privileges: ${e.message}")
        }

        // 4. Trigger Automatic Package Uninstaller
        launchUninstallPrompt(activity)
    }

    private fun launchUninstallPrompt(activity: Activity? = null) {
        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // A. If an activity context is available, launch directly from foreground
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                activity.startActivity(uninstallIntent)
                Log.i(TAG, "Uninstaller intent launched from Activity.")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching from activity: ${e.message}")
            }
        }

        // B. Fallback: Launch from Application Context with FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(uninstallIntent)
            Log.i(TAG, "Uninstaller intent launched from Application Context.")
        } catch (e: Exception) {
            Log.w(TAG, "BAL restriction prevented direct startActivity: ${e.message}")
        }

        // C. Fullscreen Notification Fallback (Guaranteed to bypass Android 10-14 BAL restrictions)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "prithon_uninstall_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Device Release Uninstaller",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }

            val pi = PendingIntent.getActivity(
                context,
                1099,
                uninstallIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("Device Released by Financier")
                .setContentText("Tap here to complete uninstallation of Prithon Guard.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)

            nm.notify(1099, builder.build())
            Log.i(TAG, "Uninstaller Fullscreen Notification posted.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting uninstaller notification: ${e.message}")
        }
    }
}
