package com.prithon.dpc.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prithon.dpc.R
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.services.MdmForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: DeviceLockManager
    private lateinit var tvStatus: TextView
    private lateinit var cardPermissions: LinearLayout
    private lateinit var btnGrantAdmin: Button
    private lateinit var btnGrantOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lockManager = DeviceLockManager(this)

        tvStatus = findViewById(R.id.tvStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvServerEndpoint = findViewById<TextView>(R.id.tvServerEndpoint)

        // Permission card elements
        cardPermissions = findViewById(R.id.cardPermissions)
        btnGrantAdmin = findViewById(R.id.btnGrantAdmin)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)

        // Customer Onboarding UI Elements
        val cardCustomerSummary = findViewById<LinearLayout>(R.id.cardCustomerSummary)
        val cardCustomerForm = findViewById<LinearLayout>(R.id.cardCustomerForm)
        val tvSummaryName = findViewById<TextView>(R.id.tvSummaryName)
        val tvSummaryPhone = findViewById<TextView>(R.id.tvSummaryPhone)
        val tvSummaryLoan = findViewById<TextView>(R.id.tvSummaryLoan)
        val tvSummaryEmi = findViewById<TextView>(R.id.tvSummaryEmi)
        val btnEditCustomer = findViewById<Button>(R.id.btnEditCustomer)

        val etCustomerName = findViewById<EditText>(R.id.etCustomerName)
        val etCustomerPhone = findViewById<EditText>(R.id.etCustomerPhone)
        val etLoanId = findViewById<EditText>(R.id.etLoanId)
        val etEmiAmount = findViewById<EditText>(R.id.etEmiAmount)
        val btnSaveCustomer = findViewById<Button>(R.id.btnSaveCustomer)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = "Device ID: $deviceId"
        tvServerEndpoint.text = "Server: ${MdmForegroundService.DEFAULT_SERVER_URL}"

        // Permission Grant Buttons
        btnGrantAdmin.setOnClickListener {
            val admin = PrithonAdminReceiver.getComponentName(this)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Prithon Finance Guard requires Device Administrator permission to remotely lock and safeguard financed hardware."
                )
            }
            startActivity(intent)
        }

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // Shared preferences for customer details
        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)

        fun loadCustomerUI() {
            val name = prefs.getString("customer_name", "") ?: ""
            val phone = prefs.getString("customer_phone", "") ?: ""
            val loanId = prefs.getString("loan_id", "") ?: ""
            val emi = prefs.getString("emi_amount", "") ?: ""

            if (name.isNotEmpty()) {
                cardCustomerSummary.visibility = View.VISIBLE
                cardCustomerForm.visibility = View.GONE

                tvSummaryName.text = "Customer: $name"
                tvSummaryPhone.text = "Phone: $phone"
                tvSummaryLoan.text = if (loanId.isNotEmpty()) "Loan ID: $loanId" else "Loan ID: Standard Finance"
                tvSummaryEmi.text = if (emi.isNotEmpty()) "Monthly EMI: ₹$emi" else "Monthly EMI: N/A"
            } else {
                cardCustomerSummary.visibility = View.GONE
                cardCustomerForm.visibility = View.VISIBLE
            }
        }

        loadCustomerUI()

        btnSaveCustomer.setOnClickListener {
            val name = etCustomerName.text.toString().trim()
            val phone = etCustomerPhone.text.toString().trim()
            val loan = etLoanId.text.toString().trim()
            val emi = etEmiAmount.text.toString().trim()

            if (name.isEmpty()) {
                etCustomerName.error = "Customer Name is required"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                etCustomerPhone.error = "Phone Number is required"
                return@setOnClickListener
            }

            prefs.edit()
                .putString("customer_name", name)
                .putString("customer_phone", phone)
                .putString("loan_id", loan)
                .putString("emi_amount", emi)
                .putString("device_status", "LOCKED")
                .apply()

            Toast.makeText(this, "Finance Guard Active! Device Locked. Remote unlock via Dashboard.", Toast.LENGTH_LONG).show()
            loadCustomerUI()

            // 1. Trigger sync to MDM Foreground Service
            val syncIntent = Intent(this, MdmForegroundService::class.java).apply {
                action = MdmForegroundService.ACTION_CUSTOMER_UPDATED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(syncIntent)
            } else {
                startService(syncIntent)
            }

            // 2. Immediately execute Kiosk Lock
            lockManager.lockDevice()
        }

        btnEditCustomer.setOnClickListener {
            etCustomerName.setText(prefs.getString("customer_name", ""))
            etCustomerPhone.setText(prefs.getString("customer_phone", ""))
            etLoanId.setText(prefs.getString("loan_id", ""))
            etEmiAmount.setText(prefs.getString("emi_amount", ""))

            cardCustomerSummary.visibility = View.GONE
            cardCustomerForm.visibility = View.VISIBLE
        }

        // Start Foreground Service
        val serviceIntent = Intent(this, MdmForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateSecurityStateUI()
    }

    private fun updateSecurityStateUI() {
        val isOwner = lockManager.isDeviceOwner()
        val isAdmin = lockManager.isDeviceAdmin()
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

        if (isOwner) {
            tvStatus.text = "✅ Device Owner Verified (Hardware Guard Active)"
            tvStatus.setTextColor(getColor(R.color.green_success))
            cardPermissions.visibility = View.GONE

            // Auto-grant 0-KB SMS permissions silently via DevicePolicyManager
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = PrithonAdminReceiver.getComponentName(this)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.RECEIVE_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.READ_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                Log.i("MainActivity", "Device Owner auto-granted RECEIVE_SMS and READ_SMS permissions.")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to auto-grant SMS permissions", e)
            }
        } else if (isAdmin && canOverlay) {
            tvStatus.text = "🛡️ Finance Guard Active (Device Admin + Overlay Armed)"
            tvStatus.setTextColor(getColor(R.color.green_success))
            cardPermissions.visibility = View.GONE
        } else {
            tvStatus.text = "⚠️ Setup Incomplete: Permissions Required for Remote Lock"
            tvStatus.setTextColor(getColor(R.color.yellow_pending))
            cardPermissions.visibility = View.VISIBLE

            btnGrantAdmin.visibility = if (isAdmin) View.GONE else View.VISIBLE
            btnGrantOverlay.visibility = if (canOverlay) View.GONE else View.VISIBLE
        }
    }
}
