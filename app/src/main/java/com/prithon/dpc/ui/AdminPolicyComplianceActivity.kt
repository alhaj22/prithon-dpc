package com.prithon.dpc.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("AdminCompliance", "ADMIN_POLICY_COMPLIANCE called by SetupWizard.")

        setResult(RESULT_OK, Intent())
        finish()
    }
}
