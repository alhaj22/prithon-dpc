package com.prithon.dpc.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

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
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Day-0 Android Enterprise Provisioning COMPLETE. Device Owner active!")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Reference: Google TestDPC (b/177617306). On Android 8.0+ (and especially Android 12, 13, 14),
            // SetupWizard is still in progress in foreground and handles lifecycle via
            // ACTION_ADMIN_POLICY_COMPLIANCE and ACTION_PROVISIONING_SUCCESSFUL.
            // Executing heavy DPM policy mutations or network locks here aborts SetupWizard with "Something went wrong".
            return
        }
    }
}
