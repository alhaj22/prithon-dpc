package com.prithon.dpc.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
        if (lockManager.isDeviceOwner() && !isUnlocked) {
            try {
                startLockTask()
            } catch (e: Exception) {
                // Ignore
            }
        }
        val filter = IntentFilter(MdmForegroundService.ACTION_REMOTE_UNLOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteUnlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(remoteUnlockReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(remoteUnlockReceiver)
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
