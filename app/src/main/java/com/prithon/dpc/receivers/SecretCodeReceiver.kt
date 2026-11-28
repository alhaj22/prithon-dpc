package com.prithon.dpc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.prithon.dpc.ui.MainActivity

/**
 * Emergency Dialer Secret Code Receiver
 * Dialing *#*#7748#*#* (*#*#PRIT#*#*) opens the admin configuration console
 * even when the launcher icon is completely hidden from the user.
 */
class SecretCodeReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SecretCodeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Secret code *#*#7748#*#* dialed on device! Launching Prithon Admin Activity...")
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(launchIntent)
    }
}
