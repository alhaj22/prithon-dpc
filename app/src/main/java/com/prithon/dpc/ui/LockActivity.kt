package com.prithon.dpc.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    private val remoteUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MdmForegroundService.ACTION_REMOTE_UNLOCK) {
                Toast.makeText(this@LockActivity, "Device Unlocked by Server", Toast.LENGTH_SHORT).show()
                try {
                    stopLockTask()
                } catch (e: Exception) {
                    // Ignore
                }
                lockManager.unlockDevice(this@LockActivity)
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        lockManager = DeviceLockManager(this)

        // Strict Kiosk Pinning
        if (lockManager.isDeviceOwner()) {
            startLockTask()
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
                lockManager.unlockDevice(this)
            } else {
                Toast.makeText(this, "Invalid PIN! Please contact your retailer.", Toast.LENGTH_SHORT).show()
            }
        }

        // RBI Compliance: Universal Emergency 112 Access
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

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MdmForegroundService.ACTION_REMOTE_UNLOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteUnlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(remoteUnlockReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(remoteUnlockReceiver)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Suppress back button navigation completely
    }
}
