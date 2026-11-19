package com.prithon.dpc.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.prithon.dpc.services.MdmForegroundService

class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch MDM Foreground Service on successful Day-0 enrollment
        val serviceIntent = Intent(this, MdmForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setResult(RESULT_OK, Intent())
        finish()
    }
}
