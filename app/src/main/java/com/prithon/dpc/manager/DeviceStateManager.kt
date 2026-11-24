package com.prithon.dpc.manager

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Enterprise State Manager that persists device lock status across
 * both standard Credential-Encrypted Storage and Direct Boot (Device-Protected Storage).
 *
 * Ensures that if a device is forcefully rebooted via hardware buttons while locked,
 * the Direct Boot storage immediately confirms the LOCKED state on boot BEFORE user password entry!
 */
object DeviceStateManager {

    private const val TAG = "DeviceStateManager"
    private const val PREFS_NAME = "prithon_customer_prefs"
    private const val KEY_STATUS = "device_status"

    fun setDeviceStatus(context: Context, status: String) {
        // 1. Regular Credential-Encrypted Storage
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_STATUS, status).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to CE storage: ${e.message}")
        }

        // 2. Direct Boot Device-Protected Storage (survives reboot before first user unlock)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val directBootContext = context.createDeviceProtectedStorageContext()
                val directPrefs = directBootContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                directPrefs.edit().putString(KEY_STATUS, status).apply()
                Log.i(TAG, "Device status '$status' successfully committed to Direct Boot Storage.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save to Direct Boot storage: ${e.message}")
            }
        }
    }

    fun getDeviceStatus(context: Context): String {
        // Check regular CE storage first
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val status = prefs.getString(KEY_STATUS, null)
            if (!status.isNullOrEmpty()) return status
        } catch (e: Exception) {
            // Locked before user unlock
        }

        // Check Direct Boot storage
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val directBootContext = context.createDeviceProtectedStorageContext()
                val directPrefs = directBootContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val status = directPrefs.getString(KEY_STATUS, null)
                if (!status.isNullOrEmpty()) return status
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read Direct Boot storage: ${e.message}")
            }
        }

        return "NORMAL"
    }

    fun isDeviceLocked(context: Context): Boolean {
        return getDeviceStatus(context) == "LOCKED"
    }
}
