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
     * 2. Device Owner Kiosk Pinning (if DO)
     * 3. Instant System Overlay View on WindowManager (draws over ANY running app)
     * 4. Direct Fullscreen LockActivity Launch
     * 5. High-Priority FullScreen Notification Intent
     * (NOTE: dpm.lockNow is intentionally NOT called so screen does NOT shut off)
     */
    fun lockDevice() {
        isKioskLocked = true
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

        // 2. Hardware-level policies if Device Owner
        if (isOwner) {
            try {
                dpm.setLockTaskPackages(admin, arrayOf(context.packageName))
                dpm.setStatusBarDisabled(admin, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(
                        admin,
                        DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                        DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                    )
                }
                Log.i(TAG, "Device Owner hardware lock policies applied.")
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
            )

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
}
