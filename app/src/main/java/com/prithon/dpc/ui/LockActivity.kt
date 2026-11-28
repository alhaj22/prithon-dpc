package com.prithon.dpc.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Emergency
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prithon.dpc.R
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.services.MdmForegroundService
import com.prithon.dpc.ui.theme.PureWhite
import com.prithon.dpc.ui.theme.PrithonDpcTheme
import com.prithon.dpc.ui.theme.Red50
import com.prithon.dpc.ui.theme.Red600
import com.prithon.dpc.ui.theme.Red700
import com.prithon.dpc.ui.theme.Slate200
import com.prithon.dpc.ui.theme.Slate50
import com.prithon.dpc.ui.theme.Slate500
import com.prithon.dpc.ui.theme.Slate900

class LockActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LockActivity"
        private const val OFFLINE_MASTER_PIN = "998877"
    }

    private lateinit var lockManager: DeviceLockManager
    private var isUnlocked = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val remoteUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MdmForegroundService.ACTION_REMOTE_UNLOCK) {
                Log.i(TAG, "Device Unlocked by Server Command.")
                Toast.makeText(this@LockActivity, "Device Unlocked by Server", Toast.LENGTH_SHORT).show()
                performUnlock()
            }
        }
    }

    private val systemDialogsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason = intent.getStringExtra("reason")
                Log.i(TAG, "Intercepted system dialog: $reason. Auto-dismissing Power / Global Dialogs!")
                if (!isUnlocked && isLockedState()) {
                    dismissSystemDialogs()
                    collapseStatusBar()
                }
            }
        }
    }

    private fun isLockedState(): Boolean {
        return DeviceLockManager.isKioskLocked || DeviceStateManager.isDeviceLocked(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Suppress back button completely while locked
            }
        })

        lockManager = DeviceLockManager(this)
        enforceDeviceOwnerLockTask()

        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
        val customerName = prefs.getString("customer_name", "") ?: ""
        val loanId = prefs.getString("loan_id", "") ?: ""
        val emiAmount = prefs.getString("emi_amount", "") ?: ""
        val tenantCode = prefs.getString("tenant_code", "") ?: ""

        setContent {
            PrithonDpcTheme(darkStatusBar = true) {
                LockScreenContent(
                    customerName = customerName,
                    loanId = loanId,
                    emiAmount = emiAmount,
                    tenantCode = tenantCode,
                    onAttemptUnlock = { pin ->
                        if (pin.trim() == OFFLINE_MASTER_PIN) {
                            Toast.makeText(this, "Master PIN Accepted. Unlocking...", Toast.LENGTH_SHORT).show()
                            val intent = Intent(MdmForegroundService.ACTION_PIN_UNLOCKED).apply {
                                setPackage(packageName)
                            }
                            sendBroadcast(intent)
                            performUnlock()
                            true
                        } else {
                            false
                        }
                    },
                    onEmergencyCall = {
                        val emergencyIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(emergencyIntent)
                    },
                    onSupportCall = {
                        val supportIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1800123456")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(supportIntent)
                    }
                )
            }
        }
    }

    private fun enforceDeviceOwnerLockTask() {
        if (lockManager.isDeviceOwner()) {
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = PrithonAdminReceiver.getComponentName(this)
                dpm.setLockTaskPackages(admin, arrayOf(packageName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
                dpm.setStatusBarDisabled(admin, true)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_FACTORY_RESET)
                startLockTask()
                Log.i(TAG, "Device Owner LockTask active with LOCK_TASK_FEATURE_NONE.")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start lock task: ${e.message}")
            }
        }
    }

    private fun dismissSystemDialogs() {
        try {
            @Suppress("DEPRECATION")
            sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        } catch (_: Exception) {
        }
    }

    @SuppressLint("WrongConstant")
    private fun collapseStatusBar() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapse = statusBarManager.getMethod("collapsePanels")
            collapse.invoke(statusBarService)
        } catch (_: Exception) {
        }
    }

    private fun hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(
                        android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars()
                    )
                    controller.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            }
        } catch (_: Exception) {
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!isUnlocked && isLockedState()) {
            hideSystemBars()
            if (!hasFocus) {
                Log.w(TAG, "Window lost focus while locked! Intercepting power/status menu...")
                dismissSystemDialogs()
                collapseStatusBar()
                mainHandler.postDelayed({
                    if (!isUnlocked && isLockedState()) {
                        dismissSystemDialogs()
                        collapseStatusBar()
                        val intent = Intent(this, LockActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        }
                        startActivity(intent)
                    }
                }, 50)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isUnlocked && isLockedState() && event.keyCode == KeyEvent.KEYCODE_POWER) {
            Log.w(TAG, "Hardware POWER button pressed in LockActivity! Dismissing dialogs!")
            dismissSystemDialogs()
            collapseStatusBar()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun performUnlock() {
        isUnlocked = true
        DeviceLockManager.isKioskLocked = false
        DeviceStateManager.setDeviceStatus(this, "NORMAL")
        try {
            stopLockTask()
        } catch (_: Exception) {
        }
        lockManager.unlockDevice(this)
        finishAndRemoveTask()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        enforceDeviceOwnerLockTask()

        val unlockFilter = IntentFilter(MdmForegroundService.ACTION_REMOTE_UNLOCK)
        val dialogsFilter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteUnlockReceiver, unlockFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(systemDialogsReceiver, dialogsFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(remoteUnlockReceiver, unlockFilter)
            registerReceiver(systemDialogsReceiver, dialogsFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(remoteUnlockReceiver)
            unregisterReceiver(systemDialogsReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isUnlocked && isLockedState()) {
            val intent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
    }
}

@Composable
private fun LockScreenContent(
    customerName: String,
    loanId: String,
    emiAmount: String,
    tenantCode: String,
    onAttemptUnlock: (String) -> Boolean,
    onEmergencyCall: () -> Unit,
    onSupportCall: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .verticalScroll(rememberScrollState())
    ) {
        // High-Contrast Alert Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Red700)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = PureWhite.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(36.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.device_locked_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.device_locked_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite.copy(alpha = 0.92f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loan / Customer Summary Card (if bound)
            if (customerName.isNotBlank() || loanId.isNotBlank() || emiAmount.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "EMI Account Summary",
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate900
                        )
                        HorizontalDivider(color = Slate200)
                        if (customerName.isNotBlank()) {
                            LockInfoRow("Customer", customerName)
                        }
                        if (tenantCode.isNotBlank()) {
                            LockInfoRow("Retailer Code", tenantCode)
                        }
                        if (loanId.isNotBlank()) {
                            LockInfoRow("Loan ID", loanId)
                        }
                        if (emiAmount.isNotBlank()) {
                            LockInfoRow("Monthly EMI", "₹$emiAmount")
                        }
                    }
                }
            }

            // Offline Master PIN Unlock Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                border = BorderStroke(1.dp, Slate200),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = Red600)
                        Text(
                            text = "Retailer / Offline Unlock",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it.filter { ch -> ch.isDigit() }.take(6)
                            pinError = null
                        },
                        label = { Text(stringResource(R.string.unlock_pin_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (!onAttemptUnlock(pinInput)) {
                                pinError = "Invalid PIN. Please contact your retailer."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(stringResource(R.string.unlock_btn))
                    }
                }
            }

            // Emergency & Helpline Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onEmergencyCall,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Red50,
                        contentColor = Red600
                    ),
                    border = BorderStroke(1.dp, Red600.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Emergency, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("112 SOS")
                }

                OutlinedButton(
                    onClick = onSupportCall,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("HELPLINE")
                }
            }
        }
    }
}

@Composable
private fun LockInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Slate500)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
