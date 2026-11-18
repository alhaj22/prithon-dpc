package com.prithon.dpc.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.prithon.dpc.services.MdmForegroundService

class PrithonAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "PrithonAdminReceiver"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, PrithonAdminReceiver::class.java)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin Privileges successfully ENABLED.")
        startMdmService(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Day-0 Android Enterprise Provisioning COMPLETE. Device Owner active!")

        // Enforce Day-0 System Hardening (Anti-Tamper Sealing)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = getComponentName(context)

        try {
            // 1. Prevent Factory Reset from Settings
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            // 2. Prevent Safe Mode Reboot (Anti-Bypass)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            // 3. Prevent USB Data Transfer / MTP
            dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            // 4. Block Developer Options / USB Debugging
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            // 5. Block adding secondary guest accounts
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)

            Log.i(TAG, "Hardware Sealing Policies enforced successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying hardware sealing policies", e)
        }

        startMdmService(context)
    }

    private fun startMdmService(context: Context) {
        val serviceIntent = Intent(context, MdmForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
