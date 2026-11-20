package com.prithon.dpc.ui

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.prithon.dpc.receivers.PrithonAdminReceiver

class ProvisioningSuccessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("ProvisioningSuccess", "Device Owner Provisioning Finalized Successfully.")

        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = PrithonAdminReceiver.getComponentName(this)
            if (dpm.isDeviceOwnerApp(packageName)) {
                // Auto-grant runtime SMS & Notification permissions silently
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.RECEIVE_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.READ_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.POST_NOTIFICATIONS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                Log.i("ProvisioningSuccess", "Auto-granted runtime permissions.")
            }
        } catch (e: Exception) {
            Log.e("ProvisioningSuccess", "Error in post-provisioning permissions", e)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(launchIntent)
        finish()
    }
}
