package com.prithon.dpc.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class AppInventoryManager(private val context: Context) {

    companion object {
        private const val TAG = "AppInventoryManager"
        private const val ICON_SIZE = 64
    }

    fun getInstalledAppsJson(): JSONArray {
        val appsArray = JSONArray()
        val pm = context.packageManager

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val processedPackages = HashSet<String>()

            for (resolveInfo in resolveInfos) {
                try {
                    val packageName = resolveInfo.activityInfo.packageName
                    if (processedPackages.contains(packageName)) continue
                    processedPackages.add(packageName)

                    val appName = resolveInfo.loadLabel(pm).toString()
                    val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    var versionName = "1.0.0"
                    try {
                        val pkgInfo = pm.getPackageInfo(packageName, 0)
                        versionName = pkgInfo.versionName ?: "1.0.0"
                    } catch (e: Exception) {
                        // ignore
                    }

                    val iconDrawable = resolveInfo.loadIcon(pm)
                    val base64Icon = drawableToBase64(iconDrawable)

                    val appObj = JSONObject().apply {
                        put("name", appName)
                        put("packageName", packageName)
                        put("version", versionName)
                        put("isSystem", isSystem)
                        put("icon", base64Icon)
                    }
                    appsArray.put(appObj)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing app item", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying installed apps", e)
        }

        return appsArray
    }

    private fun drawableToBase64(drawable: Drawable?): String {
        if (drawable == null) return ""
        try {
            val bitmap = when (drawable) {
                is BitmapDrawable -> {
                    if (drawable.bitmap != null && drawable.bitmap.width > 0 && drawable.bitmap.height > 0) {
                        Bitmap.createScaledBitmap(drawable.bitmap, ICON_SIZE, ICON_SIZE, true)
                    } else {
                        createBitmapFromDrawable(drawable)
                    }
                }
                else -> createBitmapFromDrawable(drawable)
            }

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, outputStream)
            val byteArray = outputStream.toByteArray()
            return "data:image/png;base64," + Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert drawable to base64", e)
            return ""
        }
    }

    private fun createBitmapFromDrawable(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
        drawable.draw(canvas)
        return bitmap
    }
}
