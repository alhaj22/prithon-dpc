package com.prithon.dpc.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.AppInventoryManager
import com.prithon.dpc.manager.DeviceStateManager
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

class MdmForegroundService : Service() {

    private var socket: Socket? = null
    private lateinit var lockManager: DeviceLockManager
    private lateinit var appInventoryManager: AppInventoryManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pinUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIN_UNLOCKED) {
                Log.i(TAG, "PIN unlock detected on device. Updating server status to NORMAL...")
                lockManager.unlockDevice()
                notifyDeviceStatus("NORMAL")
            }
        }
    }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            mainHandler.postDelayed(this, 10000)
        }
    }

    companion object {
        private const val TAG = "MdmForegroundService"
        private const val CHANNEL_ID = "prithon_mdm_channel"
        private const val NOTIFICATION_ID = 1001

        // Enterprise Multi-Gateway Endpoints (Cloud Public HTTPS + Local Fallbacks)
        const val CLOUD_SERVER_URL = "https://prithon-mdm-backend.onrender.com"
        const val DEFAULT_SERVER_URL = "https://prithon-mdm-backend.onrender.com"
        const val USB_LOCAL_SERVER_URL = "http://127.0.0.1:4000"
        const val ACTION_REMOTE_UNLOCK = "com.prithon.dpc.ACTION_REMOTE_UNLOCK"
        const val ACTION_PIN_UNLOCKED = "com.prithon.dpc.ACTION_PIN_UNLOCKED"
        const val ACTION_CUSTOMER_UPDATED = "com.prithon.dpc.ACTION_CUSTOMER_UPDATED"
    }

    private val endpoints = listOf(
        CLOUD_SERVER_URL,
        DEFAULT_SERVER_URL,
        USB_LOCAL_SERVER_URL
    )
    private var currentEndpointIndex = 0

    private val reconnectRunnable = Runnable {
        currentEndpointIndex = (currentEndpointIndex + 1) % endpoints.size
        connectToControlPlane()
    }

    override fun onCreate() {
        super.onCreate()
        lockManager = DeviceLockManager(this)
        appInventoryManager = AppInventoryManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        val filter = IntentFilter(ACTION_PIN_UNLOCKED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pinUnlockReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(pinUnlockReceiver, filter)
        }

        connectToControlPlane()
        mainHandler.post(heartbeatRunnable)

        // Persistent Lock Check: If device was LOCKED in CE or Direct Boot, re-lock immediately!
        if (DeviceStateManager.isDeviceLocked(this)) {
            Log.w(TAG, "Restoring active KIOSK LOCK on MdmForegroundService startup!")
            mainHandler.postDelayed({
                lockManager.lockDevice()
            }, 500)
        }
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
        val targetUrl = endpoints[currentEndpointIndex]
        Log.i(TAG, "Attempting connection to gateway: $targetUrl")
        try {
            val options = IO.Options().apply {
                reconnection = false
                timeout = 6000
            }

            socket?.disconnect()
            socket?.close()
            socket = IO.socket(targetUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.i(TAG, "Successfully connected to Control Plane Gateway: $targetUrl")
                sendRegistrationPayload()
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { err ->
                val errorMsg = err.getOrNull(0)?.toString() ?: "unknown"
                Log.w(TAG, "Connection error on $targetUrl ($errorMsg), rotating to next gateway...")
                rotateEndpointAndRetry()
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.w(TAG, "Socket disconnected from $targetUrl. Rotating gateway...")
                rotateEndpointAndRetry()
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
                            "GET_APPS", "REFRESH_APPS" -> {
                                Log.i(TAG, "Server requested installed applications inventory.")
                                sendAppInventoryPayload()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling remote command", e)
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize socket for $targetUrl", e)
            rotateEndpointAndRetry()
        }
    }

    private fun rotateEndpointAndRetry() {
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, 3000)
    }

    private fun sendRegistrationPayload() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
        val reg = JSONObject().apply {
            put("deviceId", deviceId)
            put("tenantCode", prefs.getString("tenant_code", "") ?: "")
            put("enteredImei", prefs.getString("entered_imei", "") ?: "")
            put("hardwareImei", prefs.getString("hardware_imei", "") ?: "")
            put("brand", Build.BRAND)
            put("model", Build.MODEL)
            put("battery", batteryPct)
            put("androidVersion", Build.VERSION.RELEASE)
            put("isDeviceOwner", lockManager.isDeviceOwner())
            put("status", prefs.getString("device_status", "NORMAL") ?: "NORMAL")
            put("customerName", prefs.getString("customer_name", "") ?: "")
            put("customerPhone", prefs.getString("customer_phone", "") ?: "")
            put("loanId", prefs.getString("loan_id", "") ?: "")
            put("emiAmount", prefs.getString("emi_amount", "") ?: "")
        }
        socket?.emit("register_device", reg)
        Log.i(TAG, "Registration payload emitted for device: $deviceId")
        sendAppInventoryPayload()
    }

    private fun sendAppInventoryPayload() {
        Thread {
            try {
                val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val appsArray = appInventoryManager.getInstalledAppsJson()
                val payload = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("apps", appsArray)
                }
                mainHandler.post {
                    socket?.emit("apps_inventory", payload)
                    Log.i(TAG, "Sent installed apps inventory (${appsArray.length()} apps) to control plane.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send apps inventory", e)
            }
        }.start()
    }

    private fun sendCustomerUpdatePayload() {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
            val status = prefs.getString("device_status", "NORMAL") ?: "NORMAL"
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("tenantCode", prefs.getString("tenant_code", "") ?: "")
                put("enteredImei", prefs.getString("entered_imei", "") ?: "")
                put("hardwareImei", prefs.getString("hardware_imei", "") ?: "")
                put("customerName", prefs.getString("customer_name", "") ?: "")
                put("customerPhone", prefs.getString("customer_phone", "") ?: "")
                put("loanId", prefs.getString("loan_id", "") ?: "")
                put("emiAmount", prefs.getString("emi_amount", "") ?: "")
                put("status", status)
            }
            socket?.emit("register_customer", payload)
            notifyDeviceStatus(status)
            Log.i(TAG, "Customer details emitted to server for device $deviceId with status $status")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit customer update", e)
        }
    }

    private fun notifyDeviceStatus(status: String) {
        DeviceStateManager.setDeviceStatus(this, status)
        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_status", status).apply()

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val payload = JSONObject().apply {
            put("deviceId", deviceId)
            put("status", status)
        }
        socket?.emit("device_status_changed", payload)
    }

    private fun sendHeartbeat() {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)

            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("battery", batteryPct)
                put("status", prefs.getString("device_status", "NORMAL") ?: "NORMAL")
                put("tenantCode", prefs.getString("tenant_code", "") ?: "")
                put("enteredImei", prefs.getString("entered_imei", "") ?: "")
                put("hardwareImei", prefs.getString("hardware_imei", "") ?: "")
                put("customerName", prefs.getString("customer_name", "") ?: "")
                put("customerPhone", prefs.getString("customer_phone", "") ?: "")
                put("loanId", prefs.getString("loan_id", "") ?: "")
                put("emiAmount", prefs.getString("emi_amount", "") ?: "")
            }
            socket?.emit("heartbeat", payload)
        } catch (e: Exception) {
            // Suppress heartbeat errors
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CUSTOMER_UPDATED) {
            sendCustomerUpdatePayload()
            sendAppInventoryPayload()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(heartbeatRunnable)
        mainHandler.removeCallbacks(reconnectRunnable)
        try {
            unregisterReceiver(pinUnlockReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        socket?.disconnect()
        socket?.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
