package com.prithon.dpc.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

class ProvisioningModeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("ProvisioningMode", "GET_PROVISIONING_MODE called by SetupWizard.")

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        } else {
            1
        }

        val resultIntent = Intent().apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, mode)
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }
}
