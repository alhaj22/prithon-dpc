package com.prithon.dpc.manager

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.ui.LockActivity

class DeviceLockManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = PrithonAdminReceiver.getComponentName(context)

    companion object {
        private const val TAG = "DeviceLockManager"
    }

    fun isDeviceOwner(): Boolean {
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    /**
     * Executes real hardware-enforced Kiosk Lock
     */
    fun lockDevice() {
        if (!isDeviceOwner()) {
            Log.e(TAG, "Cannot lock: App is NOT Device Owner!")
            return
        }

        try {
            // 1. Whitelist app for screen pinning
            dpm.setLockTaskPackages(admin, arrayOf(context.packageName))

            // 2. Disable status bar pull-down (Notification & Quick settings shade)
            dpm.setStatusBarDisabled(admin, true)

            // 3. Allow emergency dialer features in lock task
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                )
            }

            // 4. Launch Kiosk Lock Activity
            val lockIntent = Intent(context, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(lockIntent)
            Log.i(TAG, "Device successfully transitioned to KIOSK LOCK.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute lockDevice", e)
        }
    }

    /**
     * Releases device from Kiosk Lock back to normal state
     */
    fun unlockDevice(lockActivity: Activity? = null) {
        if (!isDeviceOwner()) {
            lockActivity?.finish()
            return
        }

        try {
            // 1. Release LockTask Mode by removing app from lock task packages
            dpm.setLockTaskPackages(admin, arrayOf())
            lockActivity?.stopLockTask()

            // 2. Re-enable status bar
            dpm.setStatusBarDisabled(admin, false)

            lockActivity?.finish()
            Log.i(TAG, "Device successfully RELEASED to NORMAL mode.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release unlockDevice", e)
            lockActivity?.finish()
        }
    }
}
