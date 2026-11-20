package com.prithon.dpc.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import com.prithon.dpc.R

class AdminPolicyComplianceActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finalize)
        Log.i("AdminCompliance", "ADMIN_POLICY_COMPLIANCE screen launched by SetupWizard.")

        val btnComplete = findViewById<Button>(R.id.btnCompleteSetup)
        btnComplete.setOnClickListener {
            completeSetup()
        }
    }

    private fun completeSetup() {
        Log.i("AdminCompliance", "Returning RESULT_OK to SetupWizard.")
        val resultIntent = Intent()
        if (intent != null) {
            resultIntent.putExtras(intent)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent accidental cancel during setup finalization
        completeSetup()
    }
}
