package com.callrecorder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callrecorder.app.AppLogger
import com.callrecorder.app.service.CallRecorderService
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.ContactUtils

/**
 * Listens for phone call state changes and drives [CallRecorderService].
 *
 * State machine:
 *  RINGING   → remember inbound number, wait
 *  OFFHOOK   → start recording (outbound via NEW_OUTGOING_CALL first)
 *  IDLE      → stop recording
 *
 * Note on Android 10+ (PROCESS_OUTGOING_CALLS deprecated in API 29):
 *  On API 29+ outgoing number is passed via PHONE_STATE extra "incoming_number"
 *  when state=OFFHOOK. Manufacturer ROMs still populate it on earlier APIs.
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                pendingOutgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: ""
                isIncoming = false
                AppLogger.d(TAG, "Outgoing call to: $pendingOutgoingNumber")
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state  = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?: pendingOutgoingNumber

                AppLogger.d(TAG, "Phone state: $state number: $number")

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        pendingIncomingNumber = number
                        isIncoming = true
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        val phoneNumber = if (isIncoming) pendingIncomingNumber else pendingOutgoingNumber
                        val name = ContactUtils.resolveCallerName(context, phoneNumber)

                        val prefs = context.getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)
                        if (!prefs.getBoolean(Constants.PREF_AUTO_RECORD, true)) return

                        context.startForegroundService(
                            Intent(context, CallRecorderService::class.java).apply {
                                action = Constants.ACTION_START_RECORDING
                                putExtra(Constants.EXTRA_PHONE_NUMBER, phoneNumber)
                                putExtra(Constants.EXTRA_CALLER_NAME, name)
                                putExtra(Constants.EXTRA_IS_INCOMING, isIncoming)
                            }
                        )
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        context.startForegroundService(
                            Intent(context, CallRecorderService::class.java).apply {
                                action = Constants.ACTION_STOP_RECORDING
                            }
                        )
                        pendingIncomingNumber = ""
                        pendingOutgoingNumber = ""
                        isIncoming = true
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CallStateReceiver"
        private var pendingIncomingNumber = ""
        private var pendingOutgoingNumber = ""
        private var isIncoming = true
    }
}
