package com.prithon.dpc.manager

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.prithon.dpc.services.MdmForegroundService
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun checkForUpdate(context: Context, onResult: (UpdateInfo?) -> Unit) {
        thread {
            try {
                val serverBase = MdmForegroundService.DEFAULT_SERVER_URL
                val updateEndpoint = "$serverBase/api/app-version"
                val url = URL(updateEndpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val remoteVersionCode = json.optInt("versionCode", 1)
                    val remoteVersionName = json.optString("versionName", "1.0.0")
                    val downloadUrl = json.optString("downloadUrl", "$serverBase/download/app-release.apk")
                    val releaseNotes = json.optString("releaseNotes", "New security update available")

                    val localVersion = getCurrentVersionCode(context)
                    if (remoteVersionCode > localVersion) {
                        val info = UpdateInfo(remoteVersionCode, remoteVersionName, downloadUrl, releaseNotes)
                        mainHandler.post { onResult(info) }
                        return@thread
                    }
                }
                mainHandler.post { onResult(null) }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                mainHandler.post { onResult(null) }
            }
        }
    }

    fun downloadAndInstall(
        activity: Activity,
        updateInfo: UpdateInfo,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        thread {
            try {
                val url = URL(updateInfo.downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 12000
                conn.readTimeout = 20000
                conn.connect()

                if (conn.responseCode != 200) {
                    mainHandler.post { onError("Server error: ${conn.responseCode}") }
                    return@thread
                }

                val totalLength = conn.contentLength
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                val apkFile = File(dir, "prithon-update-v${updateInfo.versionCode}.apk")
                if (apkFile.exists()) apkFile.delete()

                val input = conn.inputStream
                val output = FileOutputStream(apkFile)
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (totalLength > 0) {
                        val progress = ((downloaded * 100) / totalLength).toInt()
                        mainHandler.post { onProgress(progress) }
                    }
                }

                output.flush()
                output.close()
                input.close()

                mainHandler.post {
                    onComplete()
                    installDownloadedApk(activity, apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                mainHandler.post { onError(e.message ?: "Download failed") }
            }
        }
    }

    private fun installDownloadedApk(context: Context, apkFile: File) {
        val lockManager = DeviceLockManager(context)

        // 1. If Device Owner: Silent PackageInstaller
        if (lockManager.isDeviceOwner()) {
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                apkFile.inputStream().use { inStream ->
                    session.openWrite("prithon_package", 0, apkFile.length()).use { outStream ->
                        inStream.copyTo(outStream)
                        session.fsync(outStream)
                    }
                }

                val intent = Intent(context, com.prithon.dpc.receivers.BootCompletedReceiver::class.java).apply {
                    action = "com.prithon.dpc.INSTALL_COMPLETE"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
                )

                session.commit(pendingIntent.intentSender)
                session.close()
                Log.i(TAG, "Silent PackageInstaller session committed successfully.")
                return
            } catch (e: Exception) {
                Log.e(TAG, "Silent install failed, falling back to FileProvider intent", e)
            }
        }

        // 2. Fallback / Non-DO: Native System Package Installer via FileProvider
        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
            Log.i(TAG, "Triggered FileProvider installer intent successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "FileProvider install failed", e)
        }
    }
}
