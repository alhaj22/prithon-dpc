package com.prithon.dpc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.prithon.dpc.services.MdmForegroundService
import com.prithon.dpc.ui.LockActivity

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (Intent.ACTION_BOOT_COMPLETED == action || "android.intent.action.QUICKBOOT_POWERON" == action) {
            Log.i("BootCompletedReceiver", "Device reboot detected. Restoring MdmForegroundService...")

            // 1. Restore Foreground Service
            val serviceIntent = Intent(context, MdmForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 2. Persistent Reboot Lock Guard: If device was LOCKED prior to reboot, re-lock instantly!
            val prefs = context.getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
            val status = prefs.getString("device_status", "NORMAL") ?: "NORMAL"
            if (status == "LOCKED") {
                Log.w("BootCompletedReceiver", "Device was LOCKED prior to reboot! Immediately re-enforcing LockActivity on boot!")
                val lockIntent = Intent(context, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
                context.startActivity(lockIntent)
            }
        }
    }
}
