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
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prithon.dpc.R
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.receivers.PrithonAdminReceiver
import com.prithon.dpc.services.MdmForegroundService

class LockActivity : AppCompatActivity() {

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

    // Intercept Power Menu, Quick Settings, Home, Recents
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

        // 1. Keep display awake, display over keyguard/lockscreen, cutout edge-to-edge
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

        setContentView(R.layout.activity_lock)
        lockManager = DeviceLockManager(this)

        // 2. Strict Kiosk Pinning & Hardware Power Suppression if Device Owner
        enforceDeviceOwnerLockTask()

        val etPin = findViewById<EditText>(R.id.etUnlockPin)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        val btnEmergency = findViewById<Button>(R.id.btnEmergency)
        val btnSupport = findViewById<Button>(R.id.btnSupport)

        // Offline Master PIN Unlock
        btnUnlock.setOnClickListener {
            val pin = etPin.text.toString().trim()
            if (pin == OFFLINE_MASTER_PIN) {
                Toast.makeText(this, "Master PIN Accepted. Unlocking...", Toast.LENGTH_SHORT).show()
                val intent = Intent(MdmForegroundService.ACTION_PIN_UNLOCKED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
                performUnlock()
            } else {
                Toast.makeText(this, "Invalid PIN! Please contact your retailer.", Toast.LENGTH_SHORT).show()
            }
        }

        // Universal Emergency 112 Access
        btnEmergency.setOnClickListener {
            val emergencyIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(emergencyIntent)
        }

        // Financier Support Helpline
        btnSupport.setOnClickListener {
            val supportIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1800123456")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(supportIntent)
        }
    }

    private fun enforceDeviceOwnerLockTask() {
        if (lockManager.isDeviceOwner()) {
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val admin = PrithonAdminReceiver.getComponentName(this)

                // White-list this app for LockTask mode
                dpm.setLockTaskPackages(admin, arrayOf(packageName))

                // LOCK_TASK_FEATURE_NONE disables Global Actions (Power Menu), Home, Notifications & Keyguard!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
                dpm.setStatusBarDisabled(admin, true)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_FACTORY_RESET)

                startLockTask()
                Log.i(TAG, "Device Owner LockTask active with LOCK_TASK_FEATURE_NONE (Power Menu & Status Bar Disabled).")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start lock task: ${e.message}")
            }
        }
    }

    private fun dismissSystemDialogs() {
        try {
            @Suppress("DEPRECATION")
            val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialogs)
        } catch (e: Exception) {
            // Android 12+ restricts non-foreground broadcasts
        }
    }

    @SuppressLint("WrongConstant")
    private fun collapseStatusBar() {
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val collapse = statusBarManager.getMethod("collapsePanels")
            collapse.invoke(statusBarService)
        } catch (e: Exception) {
            // Suppress reflection warning
        }
    }

    private fun hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            // Ignore
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

                // Rapidly snatch back focus
                mainHandler.postDelayed({
                    if (!isUnlocked && isLockedState()) {
                        dismissSystemDialogs()
                        collapseStatusBar()
                        val intent = Intent(this, LockActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                    }
                }, 50)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isUnlocked && isLockedState()) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_POWER) {
                Log.w(TAG, "Hardware POWER button pressed in LockActivity! Dismissing dialogs!")
                dismissSystemDialogs()
                collapseStatusBar()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                // Allow volume if needed, or suppress
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun performUnlock() {
        isUnlocked = true
        DeviceLockManager.isKioskLocked = false
        DeviceStateManager.setDeviceStatus(this, "NORMAL")

        try {
            stopLockTask()
        } catch (e: Exception) {
            // Ignore
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
        } catch (e: Exception) {
            // Ignore
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Suppress back button completely
    }
}
