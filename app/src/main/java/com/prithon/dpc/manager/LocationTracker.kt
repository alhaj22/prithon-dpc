package com.prithon.dpc.manager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Enterprise GPS & Network Location Engine.
 *
 * Uses built-in Android LocationManager (GPS_PROVIDER + NETWORK_PROVIDER)
 * ensuring 100% compatibility across all OEM devices (itel, Transsion, Samsung, etc.)
 * with zero external library overhead.
 */
class LocationTracker(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracker"
        private const val PREFS_LOCATION = "prithon_location_prefs"
        private const val KEY_LATITUDE = "last_latitude"
        private const val KEY_LONGITUDE = "last_longitude"
        private const val KEY_ACCURACY = "last_accuracy"
        private const val KEY_TIMESTAMP = "last_timestamp"

        // Update interval: every 30 seconds or 10 meters change
        private const val MIN_TIME_MS = 30000L
        private const val MIN_DISTANCE_M = 10f
    }

    data class DeviceLocation(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long
    )

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListening = false
    private var locationCallback: ((DeviceLocation) -> Unit)? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val devLoc = DeviceLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                timestamp = location.time
            )
            saveLastLocation(devLoc)
            Log.i(TAG, "📍 New GPS Fix: ${devLoc.latitude}, ${devLoc.longitude} (±${devLoc.accuracy}m)")
            locationCallback?.invoke(devLoc)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            Log.i(TAG, "Location provider enabled: $provider")
        }
        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Location provider disabled: $provider")
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isGpsEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    fun getLastLocation(): DeviceLocation? {
        if (!hasLocationPermission()) return getCachedLocation()

        var bestLocation: Location? = null
        try {
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                        bestLocation = loc
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading last known location: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Error reading location: ${e.message}")
        }

        if (bestLocation != null) {
            val devLoc = DeviceLocation(
                latitude = bestLocation.latitude,
                longitude = bestLocation.longitude,
                accuracy = bestLocation.accuracy,
                timestamp = bestLocation.time
            )
            saveLastLocation(devLoc)
            return devLoc
        }

        return getCachedLocation()
    }

    fun startLocationUpdates(callback: (DeviceLocation) -> Unit) {
        this.locationCallback = callback
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cannot start location updates: Location permissions not granted yet.")
            return
        }

        if (isListening) return

        mainHandler.post {
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_MS,
                        MIN_DISTANCE_M,
                        locationListener,
                        Looper.getMainLooper()
                    )
                }
                if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_MS,
                        MIN_DISTANCE_M,
                        locationListener,
                        Looper.getMainLooper()
                    )
                }
                isListening = true
                Log.i(TAG, "Location updates successfully registered.")
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied requesting location updates", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed requesting location updates", e)
            }
        }
    }

    fun stopLocationUpdates() {
        if (isListening) {
            try {
                locationManager.removeUpdates(locationListener)
                isListening = false
                Log.i(TAG, "Location updates stopped.")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun saveLastLocation(loc: DeviceLocation) {
        val prefs = context.getSharedPreferences(PREFS_LOCATION, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_LATITUDE, loc.latitude.toFloat())
            .putFloat(KEY_LONGITUDE, loc.longitude.toFloat())
            .putFloat(KEY_ACCURACY, loc.accuracy)
            .putLong(KEY_TIMESTAMP, loc.timestamp)
            .apply()
    }

    private fun getCachedLocation(): DeviceLocation? {
        val prefs = context.getSharedPreferences(PREFS_LOCATION, Context.MODE_PRIVATE)
        val lat = prefs.getFloat(KEY_LATITUDE, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble()
        val acc = prefs.getFloat(KEY_ACCURACY, 0f)
        val time = prefs.getLong(KEY_TIMESTAMP, 0L)

        if (lat == 0.0 && lng == 0.0) return null
        return DeviceLocation(lat, lng, acc, time)
    }
}
