package com.prithon.dpc.data

data class SecurityStatus(
    val isDeviceOwner: Boolean = false,
    val isDeviceAdmin: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isLocked: Boolean = false
) {
    val isFullyProtected: Boolean
        get() = isDeviceOwner || (isDeviceAdmin && hasOverlayPermission && hasAccessibilityEnabled)

    val completedStepsCount: Int
        get() = if (isDeviceOwner) 3 else {
            listOf(isDeviceAdmin, hasOverlayPermission, hasAccessibilityEnabled).count { it }
        }
}

data class CustomerProfile(
    val tenantCode: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val enteredImei: String = "",
    val hardwareImei: String = "",
    val loanId: String = "",
    val emiAmount: String = "",
    val deviceStatus: String = "NORMAL"
) {
    val isRegistered: Boolean
        get() = customerName.isNotBlank()
}

data class DeviceIdentity(
    val androidId: String = "",
    val manufacturer: String = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
    val model: String = android.os.Build.MODEL,
    val androidVersion: String = android.os.Build.VERSION.RELEASE ?: "14",
    val appVersionName: String = "1.4.0",
    val appVersionCode: Long = 5L
)

enum class ServerPreset(val label: String, val subtitle: String, val url: String) {
    CLOUD("Cloud Server", "Production Render Gateway", "https://prithon-mdm-backend.onrender.com"),
    LOCAL_WIFI("Local Wi-Fi", "Retailer LAN Gateway", "http://192.168.1.3:4000"),
    USB_LOCAL("USB Loopback", "ADB Reverse Tunnel", "http://127.0.0.1:4000")
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    ) : UpdateUiState
    data class Downloading(val versionName: String, val progress: Int) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}
