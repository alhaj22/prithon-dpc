package com.prithon.dpc.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class ProvisioningSuccessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("ProvisioningSuccess", "Device Owner Provisioning Finalized Successfully.")

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(launchIntent)
        finish()
    }
}
