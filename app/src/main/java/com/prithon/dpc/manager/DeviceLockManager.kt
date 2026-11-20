package com.prithon.dpc.manager

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.ui.LockActivity

class DeviceLockManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = PrithonAdminReceiver.getComponentName(context)

    companion object {
        private const val TAG = "DeviceLockManager"
        private const val LOCK_CHANNEL_ID = "prithon_lock_channel"
        private const val LOCK_NOTIFICATION_ID = 9999
        @Volatile
        var isKioskLocked: Boolean = false
    }

    fun isDeviceOwner(): Boolean {
        return try {
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes Kiosk Lock (Dual Mode: Hardware Device Owner Kiosk + Universal Fullscreen Kiosk Guard)
     */
    fun lockDevice() {
        isKioskLocked = true
        val isOwner = isDeviceOwner()
        Log.i(TAG, "Initiating lockDevice... isDeviceOwner=$isOwner")

        // 1. Hardware-level policies if Device Owner
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
                Log.i(TAG, "Device Owner policies successfully enforced.")
            } catch (e: Exception) {
                Log.w(TAG, "Device Owner policy warning: ${e.message}")
            }
        }

        // 2. Launch Kiosk Lock Activity immediately
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
            Log.i(TAG, "Direct startActivity(LockActivity) executed.")
        } catch (e: Exception) {
            Log.e(TAG, "Direct startActivity failed, falling back to full-screen intent", e)
        }

        // 3. Fallback Fullscreen Intent via High-Priority Notification for background reliability
        launchFullScreenLockNotification()
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
                .setContentText("Device is locked. Complete EMI payment to unlock.")
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

        // Dismiss full-screen lock notification
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(LOCK_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }

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
