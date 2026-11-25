package com.prithon.dpc.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.prithon.dpc.R
import com.prithon.dpc.manager.AppUpdateManager
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.manager.UpdateInfo
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.services.MdmForegroundService
import com.prithon.dpc.services.PrithonAntiPowerOffService

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: DeviceLockManager
    private lateinit var tvStatus: TextView
    private lateinit var cardPermissions: LinearLayout
    private lateinit var btnGrantAdmin: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantAccessibility: Button

    private var activePermissionDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lockManager = DeviceLockManager(this)

        tvStatus = findViewById(R.id.tvStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvServerEndpoint = findViewById<TextView>(R.id.tvServerEndpoint)
        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate)

        // Display version
        val vName = AppUpdateManager.getCurrentVersionName(this)
        val vCode = AppUpdateManager.getCurrentVersionCode(this)
        tvAppVersion.text = "App Version: v$vName (Build $vCode)"

        // Permission card elements
        cardPermissions = findViewById(R.id.cardPermissions)
        btnGrantAdmin = findViewById(R.id.btnGrantAdmin)
        btnGrantOverlay = findViewById(R.id.btnGrantOverlay)
        btnGrantAccessibility = findViewById(R.id.btnGrantAccessibility)

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

        // Permission Grant Buttons (Manual Fallbacks)
        btnGrantAdmin.setOnClickListener { promptDeviceAdmin() }
        btnGrantOverlay.setOnClickListener { promptOverlayPermission() }
        btnGrantAccessibility.setOnClickListener { promptAccessibilityPermission() }

        // Tapping permission card opens guided popup wizard
        cardPermissions.setOnClickListener {
            showPermissionWizardDialog()
        }

        // Check for updates button
        btnCheckUpdate.setOnClickListener {
            Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
            checkForInAppUpdate(manual = true)
        }

        // Auto-request location permissions for live GPS tracking on dashboard
        val locationPerms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (locationPerms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, locationPerms, 1001)
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
                showPermissionWizardDialog()
                return@setOnClickListener
            }

            if (!isOwner && !canOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                showPermissionWizardDialog()
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
            val currentStatus = prefs.getString("device_status", "NORMAL") ?: "NORMAL"

            prefs.edit()
                .putString("tenant_code", tenantCode)
                .putString("customer_name", name)
                .putString("customer_phone", phone)
                .putString("entered_imei", enteredImei)
                .putString("hardware_imei", hardwareImei)
                .putString("loan_id", loan)
                .putString("emi_amount", emi)
                .putString("device_status", currentStatus)
                .apply()

            DeviceStateManager.setDeviceStatus(this, currentStatus)

            Toast.makeText(this, "Customer registered! Device bound to $tenantCode. Status: $currentStatus", Toast.LENGTH_LONG).show()
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

            // If already locked, maintain kiosk lock
            if (currentStatus == "LOCKED") {
                lockManager.lockDevice()
            }
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

        val btnDeactivateAndUninstall = findViewById<Button>(R.id.btnDeactivateAndUninstall)
        btnDeactivateAndUninstall.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Deactivate Admin & Uninstall?")
                .setMessage("Kya aap Device Administrator revoke karke Prithon Finance Guard ko phone se uninstall karna chahte hain?")
                .setPositiveButton("YES, UNINSTALL") { _, _ ->
                    Toast.makeText(this, "Admin deactivate ho raha hai...", Toast.LENGTH_SHORT).show()
                    lockManager.releaseAndUninstall(this)
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        // Start Foreground Service
        val serviceIntent = Intent(this, MdmForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Silent automatic check for updates on startup
        checkForInAppUpdate(manual = false)
    }

    // ==================== GUIDED PERMISSION POP-UP WIZARD ====================

    private fun promptDeviceAdmin() {
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

    private fun promptOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun promptAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Select 'Prithon Anti-Power-Off Protection' and switch it ON", Toast.LENGTH_LONG).show()
    }

    private fun showPermissionWizardDialog() {
        val isOwner = lockManager.isDeviceOwner()
        if (isOwner) return

        val isAdmin = lockManager.isDeviceAdmin()
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        val hasAccessibility = isAccessibilityEnabled()

        if (isAdmin && canOverlay && hasAccessibility) {
            Toast.makeText(this, "✅ Sabhi permissions active hain! Device 100% secured hai.", Toast.LENGTH_SHORT).show()
            return
        }

        activePermissionDialog?.dismiss()

        if (!isAdmin) {
            activePermissionDialog = AlertDialog.Builder(this)
                .setTitle("🛡️ Step 1/3: Activate Device Admin")
                .setMessage("Prithon Finance Guard ko background me secure rakhne aur dashboard se manage karne ke liye Device Administrator allow karna anivarya (mandatory) hai.\n\nNeeche 'ALLOW NOW' par click karein.")
                .setPositiveButton("ALLOW DEVICE ADMIN") { _, _ -> promptDeviceAdmin() }
                .setNegativeButton("CANCEL", null)
                .setCancelable(false)
                .show()
        } else if (!canOverlay) {
            activePermissionDialog = AlertDialog.Builder(this)
                .setTitle("🛡️ Step 2/3: Allow Display Over Apps")
                .setMessage("EMI Overdue hone par remote lock screen display karne ke liye 'Display over other apps' allow kijiye.\n\n💡 Android 13/14 Tip:\nAgar toggle button greyed-out (disabled) hai, to phone ke Settings > Apps > Prithon DPC me jakar top-right 3-dots (⋮) par tap karein aur 'Allow restricted settings' ko ON karein.")
                .setPositiveButton("ALLOW OVERLAY") { _, _ -> promptOverlayPermission() }
                .setNegativeButton("CANCEL", null)
                .setCancelable(false)
                .show()
        } else if (!hasAccessibility) {
            activePermissionDialog = AlertDialog.Builder(this)
                .setTitle("🛡️ Step 3/3: Anti-Power-Off Guard")
                .setMessage("Lock condition me phone ko switch-off ya restart hone se rokne ke liye 'Prithon Anti-Power-Off Protection' accessibility service ON karein.\n\nNeeche button par tap karein aur accessibility settings list me switch ON karein.")
                .setPositiveButton("ALLOW ANTI-POWER-OFF") { _, _ -> promptAccessibilityPermission() }
                .setNegativeButton("CANCEL", null)
                .setCancelable(false)
                .show()
        }
    }

    // ==================== IN-APP AUTOMATIC UPDATE ENGINE ====================

    private fun checkForInAppUpdate(manual: Boolean) {
        AppUpdateManager.checkForUpdate(this) { updateInfo ->
            if (updateInfo != null) {
                showUpdateAvailableDialog(updateInfo)
            } else if (manual) {
                Toast.makeText(this, "✅ App is already up to date! (v${AppUpdateManager.getCurrentVersionName(this)})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateAvailableDialog(info: UpdateInfo) {
        AlertDialog.Builder(this)
            .setTitle("🚀 New App Update Available")
            .setMessage("Naya update available hai!\n\nVersion: v${info.versionName} (Build ${info.versionCode})\nDetails: ${info.releaseNotes}\n\nKya aap abhi update karna chahte hain?")
            .setPositiveButton("UPDATE NOW") { _, _ ->
                startInAppDownload(info)
            }
            .setNegativeButton("LATER", null)
            .setCancelable(false)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun startInAppDownload(info: UpdateInfo) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Downloading Update...")
            setMessage("Downloading v${info.versionName}. Please wait...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        AppUpdateManager.downloadAndInstall(
            activity = this,
            updateInfo = info,
            onProgress = { progress ->
                progressDialog.progress = progress
            },
            onComplete = {
                progressDialog.dismiss()
                Toast.makeText(this, "Download complete! Installing update...", Toast.LENGTH_SHORT).show()
            },
            onError = { errMsg ->
                progressDialog.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Update Failed")
                    .setMessage("Download error: $errMsg\nKripya internet check karein ya browser se download karein.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
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
            // Non-DO fallback
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
        if (DeviceStateManager.isDeviceLocked(this)) {
            Log.w("MainActivity", "Device is currently LOCKED! Bouncing directly to LockActivity.")
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(lockIntent)
            finish()
            return
        }
        updateSecurityStateUI()

        // Guided permission popup on launch / return if setup incomplete
        val isOwner = lockManager.isDeviceOwner()
        val isAdmin = lockManager.isDeviceAdmin()
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        val hasAccessibility = isAccessibilityEnabled()

        if (!isOwner && (!isAdmin || !canOverlay || !hasAccessibility)) {
            showPermissionWizardDialog()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val expectedServiceName = ComponentName(this, PrithonAntiPowerOffService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expectedServiceName, ignoreCase = true)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun updateSecurityStateUI() {
        val isOwner = lockManager.isDeviceOwner()
        val isAdmin = lockManager.isDeviceAdmin()
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        val hasAccessibility = isAccessibilityEnabled()

        if (isOwner) {
            tvStatus.text = "✅ Device Owner Verified (Full Hardware Kiosk & Power Menu Suppression Active)"
            tvStatus.setTextColor(getColor(R.color.green_success))
            cardPermissions.visibility = View.GONE

            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = PrithonAdminReceiver.getComponentName(this)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.RECEIVE_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.READ_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.ACCESS_FINE_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                dpm.setPermissionGrantState(admin, packageName, Manifest.permission.ACCESS_COARSE_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dpm.setPermissionGrantState(admin, packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLocationEnabled(admin, true)
                }
                Log.i("MainActivity", "Device Owner auto-granted SMS and GPS Location permissions.")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to auto-grant SMS permissions", e)
            }
        } else if (isAdmin && canOverlay && hasAccessibility) {
            tvStatus.text = "🛡️ Finance Guard Active (Admin + Overlay + Anti-Power-Off Armed)"
            tvStatus.setTextColor(getColor(R.color.green_success))
            cardPermissions.visibility = View.GONE
        } else {
            tvStatus.text = "⚠️ Setup Incomplete: Tap here or button to grant permissions"
            tvStatus.setTextColor(getColor(R.color.yellow_pending))
            cardPermissions.visibility = View.VISIBLE

            btnGrantAdmin.visibility = if (isAdmin) View.GONE else View.VISIBLE
            btnGrantOverlay.visibility = if (canOverlay) View.GONE else View.VISIBLE
            btnGrantAccessibility.visibility = if (hasAccessibility) View.GONE else View.VISIBLE
        }
    }
}
