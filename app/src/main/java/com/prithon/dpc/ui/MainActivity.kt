package com.prithon.dpc.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.prithon.dpc.R
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.services.MdmForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var lockManager: DeviceLockManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        lockManager = DeviceLockManager(this)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvDeviceId = findViewById<TextView>(R.id.tvDeviceId)
        val tvServerEndpoint = findViewById<TextView>(R.id.tvServerEndpoint)
        val btnTestLock = findViewById<Button>(R.id.btnTestLock)

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        tvDeviceId.text = "Device ID: $deviceId"
        tvServerEndpoint.text = "Server: ${MdmForegroundService.DEFAULT_SERVER_URL}"

        val isOwner = lockManager.isDeviceOwner()
        if (isOwner) {
            tvStatus.text = "✅ Device Owner Verified (Active Protection)"
            tvStatus.setTextColor(getColor(R.color.green_success))
        } else {
            tvStatus.text = "⚠️ Device Owner NOT set.\nProvision via 6-Tap QR or ADB."
            tvStatus.setTextColor(getColor(R.color.red_alert))
        }

        btnTestLock.setOnClickListener {
            if (lockManager.isDeviceOwner()) {
                lockManager.lockDevice()
            } else {
                Toast.makeText(this, "Cannot Lock: App must be Device Owner first!", Toast.LENGTH_LONG).show()
            }
        }

        // Start Foreground Service
        val serviceIntent = Intent(this, MdmForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}
