package com.prithon.dpc.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

class ProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1 represents DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE (Android 12+)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        } else {
            1
        }

        val resultIntent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, mode)
            } else {
                putExtra("android.app.extra.PROVISIONING_MODE", mode)
            }
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
