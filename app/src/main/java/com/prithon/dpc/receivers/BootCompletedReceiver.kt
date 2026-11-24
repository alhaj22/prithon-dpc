package com.prithon.dpc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.services.MdmForegroundService
import com.prithon.dpc.ui.LockActivity

/**
 * Enterprise Boot Receiver supporting Direct Boot & standard boot completed.
 *
 * If the device was LOCKED prior to a hardware or software reboot,
 * this receiver immediately executes Kiosk Lock and surfaces LockActivity
 * BEFORE the customer can open any app or access the home screen.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Reboot Broadcast received: action=$action")

        val validBootActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validBootActions) {
            // 1. Restore persistent Foreground Service
            try {
                val serviceIntent = Intent(context, MdmForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start MdmForegroundService on boot: ${e.message}")
            }

            // 2. Persistent Reboot Lock Guard: Check both standard and Direct Boot storage
            val isLocked = DeviceStateManager.isDeviceLocked(context)
            if (isLocked) {
                Log.w(TAG, "🚨 CRITICAL: Device was LOCKED prior to reboot! Re-enforcing LockActivity immediately on boot!")
                DeviceLockManager.isKioskLocked = true

                try {
                    val lockManager = DeviceLockManager(context)
                    lockManager.lockDevice()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to execute lockManager.lockDevice on boot: ${e.message}")
                }

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
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start LockActivity on boot: ${e.message}")
                }
            }
        }
    }
}
