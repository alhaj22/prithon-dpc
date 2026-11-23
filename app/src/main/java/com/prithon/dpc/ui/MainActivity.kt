package com.prithon.dpc.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
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
        val tvSummaryShop = findViewById<TextView>(R.id.tvSummaryShop)
        val tvSummaryName = findViewById<TextView>(R.id.tvSummaryName)
        val tvSummaryPhone = findViewById<TextView>(R.id.tvSummaryPhone)
        val tvSummaryImei = findViewById<TextView>(R.id.tvSummaryImei)
        val tvSummaryLoan = findViewById<TextView>(R.id.tvSummaryLoan)
        val tvSummaryEmi = findViewById<TextView>(R.id.tvSummaryEmi)
        val btnEditCustomer = findViewById<Button>(R.id.btnEditCustomer)

        val etTenantCode = findViewById<EditText>(R.id.etTenantCode)
        val etCustomerName = findViewById<EditText>(R.id.etCustomerName)
        val etCustomerPhone = findViewById<EditText>(R.id.etCustomerPhone)
        val etEnteredImei = findViewById<EditText>(R.id.etEnteredImei)
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

        // Shared preferences for customer & tenant details
        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)

        fun loadCustomerUI() {
            val tenantCode = prefs.getString("tenant_code", "") ?: ""
            val name = prefs.getString("customer_name", "") ?: ""
            val phone = prefs.getString("customer_phone", "") ?: ""
            val imei = prefs.getString("entered_imei", "") ?: ""
            val loanId = prefs.getString("loan_id", "") ?: ""
            val emi = prefs.getString("emi_amount", "") ?: ""

            if (name.isNotEmpty()) {
                cardCustomerSummary.visibility = View.VISIBLE
                cardCustomerForm.visibility = View.GONE

                tvSummaryShop.text = if (tenantCode.isNotEmpty()) "Shop Code: $tenantCode" else "Shop Code: Unassigned"
                tvSummaryName.text = "Customer: $name"
                tvSummaryPhone.text = "Phone: $phone"
                tvSummaryImei.text = if (imei.isNotEmpty()) "IMEI: $imei" else "IMEI: Hardware Bound"
                tvSummaryLoan.text = if (loanId.isNotEmpty()) "Loan ID: $loanId" else "Loan ID: Standard Finance"
                tvSummaryEmi.text = if (emi.isNotEmpty()) "Monthly EMI: ₹$emi" else "Monthly EMI: N/A"
            } else {
                cardCustomerSummary.visibility = View.GONE
                cardCustomerForm.visibility = View.VISIBLE
            }
        }

        loadCustomerUI()

        btnSaveCustomer.setOnClickListener {
            val isOwner = lockManager.isDeviceOwner()
            val isAdmin = lockManager.isDeviceAdmin()
            val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

            if (!isOwner && !isAdmin) {
                Toast.makeText(this, "⚠️ Please activate Device Administrator first!", Toast.LENGTH_LONG).show()
                val adminComp = PrithonAdminReceiver.getComponentName(this)
                val adminIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Device Admin permission is required to lock device.")
                }
                startActivity(adminIntent)
                return@setOnClickListener
            }

            if (!isOwner && !canOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Toast.makeText(this, "⚠️ Please grant 'Display over other apps' to allow instant lock!", Toast.LENGTH_LONG).show()
                val overlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(overlayIntent)
                return@setOnClickListener
            }

            val tenantCode = etTenantCode.text.toString().trim().uppercase()
            val name = etCustomerName.text.toString().trim()
            val phone = etCustomerPhone.text.toString().trim()
            val enteredImei = etEnteredImei.text.toString().trim()
            val loan = etLoanId.text.toString().trim()
            val emi = etEmiAmount.text.toString().trim()

            if (tenantCode.isEmpty()) {
                etTenantCode.error = "Shop Code is required (e.g. APX-4021)"
                return@setOnClickListener
            }
            if (name.isEmpty()) {
                etCustomerName.error = "Customer Name is required"
                return@setOnClickListener
            }
            if (phone.isEmpty()) {
                etCustomerPhone.error = "Phone Number is required"
                return@setOnClickListener
            }
            if (enteredImei.length != 15) {
                etEnteredImei.error = "Valid 15-digit IMEI is required"
                return@setOnClickListener
            }

            val hardwareImei = getHardwareIdentifier()

            prefs.edit()
                .putString("tenant_code", tenantCode)
                .putString("customer_name", name)
                .putString("customer_phone", phone)
                .putString("entered_imei", enteredImei)
                .putString("hardware_imei", hardwareImei)
                .putString("loan_id", loan)
                .putString("emi_amount", emi)
                .putString("device_status", "LOCKED")
                .apply()

            Toast.makeText(this, "Protection Active! Device bound to $tenantCode and locked.", Toast.LENGTH_LONG).show()
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
            etTenantCode.setText(prefs.getString("tenant_code", ""))
            etCustomerName.setText(prefs.getString("customer_name", ""))
            etCustomerPhone.setText(prefs.getString("customer_phone", ""))
            etEnteredImei.setText(prefs.getString("entered_imei", ""))
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

    @SuppressLint("HardwareIds")
    private fun getHardwareIdentifier(): String {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val imei = tm.imei
                if (!imei.isNullOrEmpty()) return imei
            }
        } catch (e: Exception) {
            // Android 10+ restricts non-DO access to IMEI
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serial = Build.getSerial()
                if (!serial.isNullOrEmpty() && serial != Build.UNKNOWN) return serial
            }
        } catch (e: Exception) {
            // Fallback
        }

        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
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
