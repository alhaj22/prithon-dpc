package com.prithon.dpc.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prithon.dpc.R
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.services.MdmForegroundService

class LockActivity : AppCompatActivity() {

    private lateinit var lockManager: DeviceLockManager
    private val OFFLINE_MASTER_PIN = "998877"
    private var isUnlocked = false

    private val remoteUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MdmForegroundService.ACTION_REMOTE_UNLOCK) {
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
                Log.i("LockActivity", "Intercepted system dialog: $reason. Dismissing Power / Global Dialogs!")
                if (!isUnlocked && DeviceLockManager.isKioskLocked) {
                    dismissSystemDialogs()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on, show over lockscreen / dismiss keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_lock)
        lockManager = DeviceLockManager(this)

        // Strict Kiosk Pinning if Device Owner
        if (lockManager.isDeviceOwner()) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Ignore
            }
        }

        val etPin = findViewById<EditText>(R.id.etUnlockPin)
        val btnUnlock = findViewById<Button>(R.id.btnUnlock)
        val btnEmergency = findViewById<Button>(R.id.btnEmergency)
        val btnSupport = findViewById<Button>(R.id.btnSupport)

        // Offline PIN Unlock
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

    private fun dismissSystemDialogs() {
        try {
            @Suppress("DEPRECATION")
            val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeDialogs)
        } catch (e: Exception) {
            // Ignore
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
        if (!isUnlocked && DeviceLockManager.isKioskLocked) {
            hideSystemBars()
            if (!hasFocus) {
                // If focus lost to power menu or notification shade, close it immediately!
                dismissSystemDialogs()
            }
        }
    }

    private fun performUnlock() {
        isUnlocked = true
        DeviceLockManager.isKioskLocked = false
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
        if (lockManager.isDeviceOwner() && !isUnlocked) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Ignore
            }
        }

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
        // If user tries to press Home or switch apps while locked, pull back to front immediately
        if (!isUnlocked && DeviceLockManager.isKioskLocked) {
            val intent = Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Suppress back button navigation completely
    }
}
