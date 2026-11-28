package com.prithon.dpc.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.ui.navigation.PrithonAppScaffold
import com.prithon.dpc.ui.theme.PrithonDpcTheme
import com.prithon.dpc.ui.viewmodel.DpcViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DpcViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshAll()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val extraServer = intent?.getStringExtra("set_server")
        if (!extraServer.isNullOrEmpty()) {
            viewModel.applyServerUrl(extraServer)
        }

        requestLocationIfNeeded()
        viewModel.ensureMdmServiceRunning()
        viewModel.checkForUpdate(manual = false)

        setContent {
            PrithonDpcTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                PrithonAppScaffold(
                    state = state,
                    onGrantAdmin = ::promptDeviceAdmin,
                    onGrantOverlay = ::promptOverlayPermission,
                    onGrantAccessibility = ::promptAccessibilityPermission,
                    onRequestLocation = ::requestLocationIfNeeded,
                    onDismissWizard = { viewModel.setPermissionWizardVisible(false) },
                    onOpenWizard = { viewModel.setPermissionWizardVisible(true) },
                    onSaveCustomer = { tenant, name, phone, imei, loan, emi ->
                        viewModel.saveCustomerProfile(tenant, name, phone, imei, loan, emi)
                    },
                    onSyncNow = { viewModel.triggerSyncNow() },
                    onCheckUpdates = { viewModel.checkForUpdate(manual = true) },
                    onConfirmUpdate = { available -> viewModel.startUpdateDownload(this, available) },
                    onDismissUpdate = { viewModel.dismissUpdateDialog() },
                    onSelectServer = { url -> viewModel.applyServerUrl(url) },
                    onDeactivateAndUninstall = { viewModel.releaseAndUninstall(this) },
                    onCopiedDeviceId = { viewModel.showMessage("Device ID copied to clipboard") },
                    onSnackbarShown = { viewModel.clearSnackbar() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (DeviceStateManager.isDeviceLocked(this)) {
            Log.w("MainActivity", "Device is currently LOCKED! Bouncing directly to LockActivity.")
            val lockIntent = Intent(this, LockActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
            startActivity(lockIntent)
            finish()
            return
        }

        viewModel.refreshAll()
        viewModel.maybePromptPermissionWizardOnLaunch()
    }

    private fun requestLocationIfNeeded() {
        val locationPerms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (locationPerms.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
        ) {
            locationPermissionLauncher.launch(locationPerms)
        }
    }

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
        Toast.makeText(
            this,
            "Select 'Prithon Anti-Power-Off Protection' and switch it ON",
            Toast.LENGTH_LONG
        ).show()
    }
}
