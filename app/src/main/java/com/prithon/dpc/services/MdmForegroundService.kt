package com.prithon.dpc.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.prithon.dpc.manager.DeviceLockManager
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class MdmForegroundService : Service() {

    private var socket: Socket? = null
    private lateinit var lockManager: DeviceLockManager
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MdmForegroundService"
        private const val CHANNEL_ID = "prithon_mdm_channel"
        private const val NOTIFICATION_ID = 1001

        // Current Mac Local Wi-Fi IP and USB Reverse Port
        const val DEFAULT_SERVER_URL = "http://192.168.1.6:4000"
        const val USB_LOCAL_SERVER_URL = "http://127.0.0.1:4000"
        const val ACTION_REMOTE_UNLOCK = "com.prithon.dpc.ACTION_REMOTE_UNLOCK"
    }

    override fun onCreate() {
        super.onCreate()
        lockManager = DeviceLockManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        connectToControlPlane()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Guard MDM Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors device security policies and finance status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Prithon Device Guard Active")
            .setContentText("Hardware security policies active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun connectToControlPlane() {
        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                timeout = 10000
            }

            // Prefer Wi-Fi IP, fallback to USB reverse port
            val primaryUrl = DEFAULT_SERVER_URL
            socket = IO.socket(primaryUrl, options)

            socket?.on(Socket.EVENT_CONNECT_ERROR) { err ->
                val errorMsg = err.getOrNull(0)?.toString() ?: "unknown"
                Log.w(TAG, "Connection error on $primaryUrl ($errorMsg), trying USB fallback...")
                try {
                    socket?.disconnect()
                    socket = IO.socket(USB_LOCAL_SERVER_URL, options)
                    setupSocketListeners()
                    socket?.connect()
                } catch (e: Exception) {
                    Log.e(TAG, "USB fallback failed", e)
                }
            }

            setupSocketListeners()
            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize socket connection", e)
        }
    }

    private fun setupSocketListeners() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.i(TAG, "Successfully connected to Control Plane Gateway!")
            sendRegistrationPayload()
        }

        socket?.on("server_command") { args ->
            try {
                val data = args[0] as JSONObject
                val command = data.optString("command")
                Log.i(TAG, "Received Remote Command: $command")

                mainHandler.post {
                    when (command) {
                        "LOCK" -> {
                            lockManager.lockDevice()
                            notifyDeviceStatus("LOCKED")
                        }
                        "UNLOCK" -> {
                            lockManager.unlockDevice()
                            val unlockIntent = Intent(ACTION_REMOTE_UNLOCK).apply {
                                setPackage(packageName)
                            }
                            sendBroadcast(unlockIntent)
                            notifyDeviceStatus("NORMAL")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling remote command", e)
            }
        }

        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.w(TAG, "Socket disconnected. Will attempt auto-reconnect...")
        }
    }

    private fun sendRegistrationPayload() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val reg = JSONObject().apply {
            put("deviceId", deviceId)
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("battery", batteryPct)
            put("androidVersion", Build.VERSION.RELEASE)
            put("isDeviceOwner", lockManager.isDeviceOwner())
            put("status", "NORMAL")
        }
        socket?.emit("register_device", reg)
        Log.i(TAG, "Registration payload emitted for device: $deviceId")
    }

    private fun notifyDeviceStatus(status: String) {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("status", status)
        }
        socket?.emit("device_status_changed", payload)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.disconnect()
        socket?.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
