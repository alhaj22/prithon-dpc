package com.prithon.dpc.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.manager.AppInventoryManager
import com.prithon.dpc.manager.DeviceStateManager
import com.prithon.dpc.manager.LocationTracker
import com.prithon.dpc.receivers.PrithonAdminReceiver
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URL

class MdmForegroundService : Service() {

    private var socket: Socket? = null
    private lateinit var lockManager: DeviceLockManager
    private lateinit var appInventoryManager: AppInventoryManager
    private lateinit var locationTracker: LocationTracker
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
        const val LOCAL_WIFI_SERVER_URL = "http://192.168.1.3:4000"
        const val USB_LOCAL_SERVER_URL = "http://127.0.0.1:4000"
        const val ACTION_REMOTE_UNLOCK = "com.prithon.dpc.ACTION_REMOTE_UNLOCK"
        const val ACTION_PIN_UNLOCKED = "com.prithon.dpc.ACTION_PIN_UNLOCKED"
        const val ACTION_CUSTOMER_UPDATED = "com.prithon.dpc.ACTION_CUSTOMER_UPDATED"
        const val ACTION_RECONNECT_SERVER = "com.prithon.dpc.ACTION_RECONNECT_SERVER"
    }

    private fun getActiveEndpoints(): List<String> {
        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
        val custom = prefs.getString("server_url", null)
        val list = mutableListOf<String>()
        if (!custom.isNullOrEmpty()) {
            list.add(custom)
        }
        if (!list.contains(LOCAL_WIFI_SERVER_URL)) list.add(LOCAL_WIFI_SERVER_URL)
        if (!list.contains(CLOUD_SERVER_URL)) list.add(CLOUD_SERVER_URL)
        if (!list.contains(USB_LOCAL_SERVER_URL)) list.add(USB_LOCAL_SERVER_URL)
        return list
    }

    private var currentEndpointIndex = 0

    private val reconnectRunnable = Runnable {
        val prefs = getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
        val custom = prefs.getString("server_url", null)
        if (!custom.isNullOrEmpty()) {
            currentEndpointIndex = 0
        } else {
            val list = getActiveEndpoints()
            currentEndpointIndex = (currentEndpointIndex + 1) % list.size
        }
        connectToControlPlane()
    }

    override fun onCreate() {
        super.onCreate()
        lockManager = DeviceLockManager(this)
        appInventoryManager = AppInventoryManager(this)
        locationTracker = LocationTracker(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // Start real-time GPS tracking listener
        locationTracker.startLocationUpdates { loc ->
            sendLocationPayload(loc)
        }

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
        val endpointsList = getActiveEndpoints()
        if (currentEndpointIndex >= endpointsList.size) {
            currentEndpointIndex = 0
        }
        val targetUrl = endpointsList[currentEndpointIndex]
        Log.i(TAG, "Attempting connection to gateway: $targetUrl")
        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 2000
                timeout = 10000
            }

            socket?.disconnect()
            socket?.close()
            socket = IO.socket(targetUrl, options)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.i(TAG, "Successfully connected to Control Plane Gateway: $targetUrl")
                mainHandler.removeCallbacks(reconnectRunnable)
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
                            "GET_LOCATION", "REFRESH_LOCATION" -> {
                                Log.i(TAG, "Server requested live GPS location.")
                                val loc = locationTracker.getLastLocation()
                                if (loc != null) {
                                    sendLocationPayload(loc)
                                }
                            }
                            "RELEASE_AND_UNINSTALL", "DELETE_DEVICE", "UNENROLL" -> {
                                Log.w(TAG, "Server issued permanent RELEASE_AND_UNINSTALL! Releasing device and removing app...")
                                lockManager.releaseAndUninstall()
                                mainHandler.postDelayed({
                                    stopSelf()
                                }, 3000)
                            }
                            "SET_WALLPAPER" -> {
                                val wallpaperUrl = data.optString("wallpaperUrl")
                                val target = data.optString("target", "both")
                                val lockChanges = data.optBoolean("lockChanges", true)
                                Log.i(TAG, "Executing SET_WALLPAPER command: $wallpaperUrl (target=$target, lockChanges=$lockChanges)")
                                if (wallpaperUrl.isNotEmpty()) {
                                    applyRemoteWallpaper(wallpaperUrl, target, lockChanges)
                                }
                            }
                            "CLEAR_WALLPAPER" -> {
                                Log.i(TAG, "Executing CLEAR_WALLPAPER command")
                                clearRemoteWallpaper()
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

            val loc = locationTracker.getLastLocation()
            if (loc != null) {
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("accuracy", loc.accuracy)
            }
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

                val loc = locationTracker.getLastLocation()
                if (loc != null) {
                    put("latitude", loc.latitude)
                    put("longitude", loc.longitude)
                    put("accuracy", loc.accuracy)
                }
            }
            socket?.emit("heartbeat", payload)
        } catch (e: Exception) {
            // Suppress heartbeat errors
        }
    }

    private fun sendLocationPayload(loc: LocationTracker.DeviceLocation) {
        try {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("latitude", loc.latitude)
                put("longitude", loc.longitude)
                put("accuracy", loc.accuracy)
            }
            socket?.emit("device_location_updated", payload)
            Log.i(TAG, "Live GPS coordinates sent to server: ${loc.latitude}, ${loc.longitude} (±${loc.accuracy}m)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending location payload: ${e.message}")
        }
    }

    private fun applyRemoteWallpaper(imageUrl: String, targetScreen: String, lockUserChanges: Boolean) {
        Thread {
            try {
                Log.i(TAG, "Downloading remote wallpaper: $imageUrl")
                val url = URL(imageUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 12000
                connection.readTimeout = 20000
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    Log.i(TAG, "Wallpaper bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")
                    val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val adminComponent = ComponentName(this, PrithonAdminReceiver::class.java)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val flags = when (targetScreen.lowercase()) {
                            "home" -> WallpaperManager.FLAG_SYSTEM
                            "lock" -> WallpaperManager.FLAG_LOCK
                            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                        }
                        try {
                            wallpaperManager.setBitmap(bitmap, null, true, flags)
                            Log.i(TAG, "Wallpaper applied via WallpaperManager (flags=$flags)")
                        } catch (wmEx: Exception) {
                            Log.w(TAG, "WallpaperManager setBitmap with flags failed: ${wmEx.message}. Trying generic setBitmap...")
                            wallpaperManager.setBitmap(bitmap)
                        }
                    } else {
                        wallpaperManager.setBitmap(bitmap)
                    }

                    if (lockUserChanges && dpm.isDeviceOwnerApp(packageName)) {
                        try {
                            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SET_WALLPAPER)
                            Log.i(TAG, "Applied UserManager.DISALLOW_SET_WALLPAPER restriction.")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not set DISALLOW_SET_WALLPAPER: ${e.message}")
                        }
                    }

                    mainHandler.post {
                        socket?.emit("device_event", JSONObject().apply {
                            put("deviceId", Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))
                            put("event", "WALLPAPER_APPLIED")
                            put("wallpaperUrl", imageUrl)
                        })
                    }
                } else {
                    Log.e(TAG, "Failed to decode bitmap from stream.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying remote wallpaper: ${e.message}", e)
            }
        }.start()
    }

    private fun clearRemoteWallpaper() {
        Thread {
            try {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(this, PrithonAdminReceiver::class.java)
                if (dpm.isDeviceOwnerApp(packageName)) {
                    try {
                        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SET_WALLPAPER)
                        Log.i(TAG, "Cleared DISALLOW_SET_WALLPAPER restriction.")
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                wallpaperManager.clear()
                Log.i(TAG, "Wallpaper cleared to factory default.")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing wallpaper: ${e.message}", e)
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CUSTOMER_UPDATED -> {
                sendCustomerUpdatePayload()
                sendAppInventoryPayload()
            }
            ACTION_RECONNECT_SERVER -> {
                Log.i(TAG, "ACTION_RECONNECT_SERVER received. Force reconnecting immediately...")
                currentEndpointIndex = 0
                mainHandler.removeCallbacks(reconnectRunnable)
                connectToControlPlane()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        locationTracker.stopLocationUpdates()
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
