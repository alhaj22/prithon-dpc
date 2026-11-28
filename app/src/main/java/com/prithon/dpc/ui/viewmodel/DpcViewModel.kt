package com.prithon.dpc.ui.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.prithon.dpc.data.CustomerProfile
import com.prithon.dpc.data.DeviceIdentity
import com.prithon.dpc.data.SecurityStatus
import com.prithon.dpc.data.UpdateUiState
import com.prithon.dpc.manager.AppUpdateManager
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.manager.UpdateInfo
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.services.MdmForegroundService
import com.prithon.dpc.services.PrithonAntiPowerOffService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DpcUiState(
    val security: SecurityStatus = SecurityStatus(),
    val customer: CustomerProfile = CustomerProfile(),
    val identity: DeviceIdentity = DeviceIdentity(),
    val serverUrl: String = MdmForegroundService.LOCAL_WIFI_SERVER_URL,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val showPermissionWizard: Boolean = false,
    val snackbarMessage: String? = null
)

class DpcViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
    private val lockManager = DeviceLockManager(context)

    private val _uiState = MutableStateFlow(DpcUiState())
    val uiState: StateFlow<DpcUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        val security = readSecurityStatus()
        val customer = readCustomerProfile()
        val identity = readDeviceIdentity()
        val serverUrl = prefs.getString("server_url", MdmForegroundService.LOCAL_WIFI_SERVER_URL)
            ?: MdmForegroundService.LOCAL_WIFI_SERVER_URL

        if (security.isDeviceOwner) {
            autoGrantDeviceOwnerPermissions()
        }

        _uiState.update {
            it.copy(
                security = security,
                customer = customer,
                identity = identity,
                serverUrl = serverUrl
            )
        }
    }

    fun maybePromptPermissionWizardOnLaunch() {
        val security = readSecurityStatus()
        if (!security.isDeviceOwner && !security.isFullyProtected) {
            _uiState.update { it.copy(showPermissionWizard = true) }
        }
    }

    fun setPermissionWizardVisible(visible: Boolean) {
        _uiState.update { it.copy(showPermissionWizard = visible) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun applyServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        if (cleanUrl.isEmpty()) return
        prefs.edit().putString("server_url", cleanUrl).apply()
        _uiState.update {
            it.copy(
                serverUrl = cleanUrl,
                snackbarMessage = "Gateway switched to $cleanUrl"
            )
        }
        val reconnectIntent = Intent(context, MdmForegroundService::class.java).apply {
            action = MdmForegroundService.ACTION_RECONNECT_SERVER
        }
        startMdmService(reconnectIntent)
    }

    fun saveCustomerProfile(
        tenantCode: String,
        name: String,
        phone: String,
        enteredImei: String,
        loanId: String,
        emiAmount: String
    ): Boolean {
        val security = readSecurityStatus()
        if (!security.isDeviceOwner && (!security.isDeviceAdmin || !security.hasOverlayPermission)) {
            _uiState.update { it.copy(showPermissionWizard = true) }
            return false
        }

        val cleanTenant = tenantCode.trim().uppercase()
        val hardwareImei = getHardwareIdentifier()
        val currentStatus = prefs.getString("device_status", "NORMAL") ?: "NORMAL"

        prefs.edit()
            .putString("tenant_code", cleanTenant)
            .putString("customer_name", name.trim())
            .putString("customer_phone", phone.trim())
            .putString("entered_imei", enteredImei.trim())
            .putString("hardware_imei", hardwareImei)
            .putString("loan_id", loanId.trim())
            .putString("emi_amount", emiAmount.trim())
            .putString("device_status", currentStatus)
            .apply()

        DeviceStateManager.setDeviceStatus(context, currentStatus)

        val syncIntent = Intent(context, MdmForegroundService::class.java).apply {
            action = MdmForegroundService.ACTION_CUSTOMER_UPDATED
        }
        startMdmService(syncIntent)

        if (currentStatus == "LOCKED") {
            lockManager.lockDevice()
        }

        refreshAll()
        showMessage("Device bound to $cleanTenant ($currentStatus)")
        return true
    }

    fun triggerSyncNow() {
        val syncIntent = Intent(context, MdmForegroundService::class.java).apply {
            action = MdmForegroundService.ACTION_CUSTOMER_UPDATED
        }
        startMdmService(syncIntent)
        showMessage("Syncing telemetry & EMI status with server...")
    }

    fun checkForUpdate(manual: Boolean) {
        if (manual) {
            _uiState.update { it.copy(updateState = UpdateUiState.Checking) }
        }
        AppUpdateManager.checkForUpdate(context) { info ->
            if (info != null) {
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Available(
                            versionCode = info.versionCode,
                            versionName = info.versionName,
                            downloadUrl = info.downloadUrl,
                            releaseNotes = info.releaseNotes
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Idle,
                        snackbarMessage = if (manual) {
                            "App is up to date (v${AppUpdateManager.getCurrentVersionName(context)})"
                        } else {
                            it.snackbarMessage
                        }
                    )
                }
            }
        }
    }

    fun startUpdateDownload(activity: Activity, available: UpdateUiState.Available) {
        val info = UpdateInfo(
            versionCode = available.versionCode,
            versionName = available.versionName,
            downloadUrl = available.downloadUrl,
            releaseNotes = available.releaseNotes
        )
        _uiState.update {
            it.copy(updateState = UpdateUiState.Downloading(available.versionName, 0))
        }
        AppUpdateManager.downloadAndInstall(
            activity = activity,
            updateInfo = info,
            onProgress = { pct ->
                _uiState.update {
                    it.copy(updateState = UpdateUiState.Downloading(available.versionName, pct))
                }
            },
            onComplete = {
                _uiState.update {
                    it.copy(
                        updateState = UpdateUiState.Idle,
                        snackbarMessage = "Update downloaded. Launching installer..."
                    )
                }
            },
            onError = { err ->
                _uiState.update {
                    it.copy(updateState = UpdateUiState.Error(err))
                }
            }
        )
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateState = UpdateUiState.Idle) }
    }

    fun releaseAndUninstall(activity: Activity) {
        lockManager.releaseAndUninstall(activity)
    }

    fun ensureMdmServiceRunning() {
        startMdmService(Intent(context, MdmForegroundService::class.java))
    }

    private fun startMdmService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun readSecurityStatus(): SecurityStatus {
        val isOwner = lockManager.isDeviceOwner()
        val isAdmin = lockManager.isDeviceAdmin()
        val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        val hasAccessibility = isAccessibilityEnabled()
        val hasLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val isLocked = DeviceStateManager.isDeviceLocked(context)

        return SecurityStatus(
            isDeviceOwner = isOwner,
            isDeviceAdmin = isAdmin,
            hasOverlayPermission = canOverlay,
            hasAccessibilityEnabled = hasAccessibility,
            hasLocationPermission = hasLocation,
            isLocked = isLocked
        )
    }

    private fun readCustomerProfile(): CustomerProfile {
        return CustomerProfile(
            tenantCode = prefs.getString("tenant_code", "") ?: "",
            customerName = prefs.getString("customer_name", "") ?: "",
            customerPhone = prefs.getString("customer_phone", "") ?: "",
            enteredImei = prefs.getString("entered_imei", "") ?: "",
            hardwareImei = prefs.getString("hardware_imei", "") ?: "",
            loanId = prefs.getString("loan_id", "") ?: "",
            emiAmount = prefs.getString("emi_amount", "") ?: "",
            deviceStatus = prefs.getString("device_status", "NORMAL") ?: "NORMAL"
        )
    }

    @SuppressLint("HardwareIds")
    private fun readDeviceIdentity(): DeviceIdentity {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        return DeviceIdentity(
            androidId = androidId,
            appVersionName = AppUpdateManager.getCurrentVersionName(context),
            appVersionCode = AppUpdateManager.getCurrentVersionCode(context)
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        return try {
            val expectedServiceName = ComponentName(context, PrithonAntiPowerOffService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
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

    private fun autoGrantDeviceOwnerPermissions() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = PrithonAdminReceiver.getComponentName(context)
            val pkg = context.packageName
            dpm.setPermissionGrantState(admin, pkg, Manifest.permission.RECEIVE_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            dpm.setPermissionGrantState(admin, pkg, Manifest.permission.READ_SMS, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            dpm.setPermissionGrantState(admin, pkg, Manifest.permission.ACCESS_FINE_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            dpm.setPermissionGrantState(admin, pkg, Manifest.permission.ACCESS_COARSE_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.setPermissionGrantState(admin, pkg, Manifest.permission.ACCESS_BACKGROUND_LOCATION, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLocationEnabled(admin, true)
            }
        } catch (e: Exception) {
            Log.e("DpcViewModel", "Failed to auto-grant DO permissions", e)
        }
    }

    @SuppressLint("HardwareIds")
    private fun getHardwareIdentifier(): String {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val imei = tm.imei
                if (!imei.isNullOrEmpty()) return imei
            }
        } catch (_: Exception) {
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serial = Build.getSerial()
                if (!serial.isNullOrEmpty() && serial != Build.UNKNOWN) return serial
            }
        } catch (_: Exception) {
        }
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
}
