package com.prithon.dpc.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.prithon.dpc.manager.DeviceLockManager
import com.prithon.dpc.services.MdmForegroundService

class SmsCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PrithonSmsReceiver"
        const val PREFIX_LOCK = "#PRITHON:LOCK"
        const val PREFIX_UNLOCK = "#PRITHON:UNLOCK"
        const val MASTER_SECRET = "ADMIN"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == "com.prithon.dpc.ACTION_SMS_SIMULATION") {
            val body = intent.getStringExtra("body") ?: ""
            val senderNumber = intent.getStringExtra("sender") ?: "9978948010"
            Log.i(TAG, "Simulated SMS triggered: $body from $senderNumber")
            if (body.contains("#PRITHON:", ignoreCase = true)) {
                handleSmsCommand(context, body, senderNumber)
            }
            return
        }

        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages: Array<SmsMessage>? = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming SMS intent", e)
            null
        }

        if (messages.isNullOrEmpty()) return

        val fullMessage = StringBuilder()
        var senderNumber = ""

        for (msg in messages) {
            fullMessage.append(msg.messageBody)
            if (senderNumber.isEmpty()) {
                senderNumber = msg.displayOriginatingAddress ?: msg.originatingAddress ?: ""
            }
        }

        val body = fullMessage.toString().trim()
        Log.i(TAG, "Incoming SMS from $senderNumber: $body")

        if (body.contains("#PRITHON:", ignoreCase = true)) {
            handleSmsCommand(context, body, senderNumber)
        }
    }

    private fun handleSmsCommand(context: Context, rawBody: String, senderNumber: String) {
        val body = rawBody.trim()
        val upper = body.uppercase()

        val lockManager = DeviceLockManager(context)
        val prefs = context.getSharedPreferences("prithon_customer_prefs", Context.MODE_PRIVATE)
        val savedLoanId = prefs.getString("loan_id", "")?.uppercase()?.trim() ?: ""

        val isLock = upper.startsWith(PREFIX_LOCK)
        val isUnlock = upper.startsWith(PREFIX_UNLOCK)

        if (!isLock && !isUnlock) return

        // Formats supported:
        // 1. #PRITHON:LOCK# or #PRITHON:LOCK
        // 2. #PRITHON:LOCK:ALHAJKHA757# or #PRITHON:LOCK:ALHAJKHA757
        // 3. #PRITHON:LOCK:ADMIN#
        val cleaned = upper.removePrefix("#").removeSuffix("#")
        val parts = cleaned.split(":")
        val passedKey = if (parts.size >= 3) parts[2].trim() else ""

        val isAuthorized = when {
            passedKey.isEmpty() -> true // Universal trigger
            passedKey == MASTER_SECRET -> true
            savedLoanId.isNotEmpty() && passedKey == savedLoanId -> true
            else -> false
        }

        if (!isAuthorized) {
            Log.w(TAG, "Unauthorized SMS command key: '$passedKey'. Expected: '$savedLoanId' or '$MASTER_SECRET'")
            return
        }

        if (isLock) {
            Log.i(TAG, "EXECUTING 0-KB SMS LOCK triggered by $senderNumber (Key: $passedKey)")
            prefs.edit().putString("device_status", "LOCKED").apply()
            lockManager.lockDevice()
            try {
                abortBroadcast()
            } catch (e: Exception) {
                // Ignore if not ordered broadcast
            }
        } else if (isUnlock) {
            Log.i(TAG, "EXECUTING 0-KB SMS UNLOCK triggered by $senderNumber (Key: $passedKey)")
            prefs.edit().putString("device_status", "NORMAL").apply()
            lockManager.unlockDevice()
            val unlockIntent = Intent(MdmForegroundService.ACTION_REMOTE_UNLOCK).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(unlockIntent)
            try {
                abortBroadcast()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
