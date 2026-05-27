package com.callrecorder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callrecorder.app.AppLogger
import com.callrecorder.app.notification.CallerNameCache
import com.callrecorder.app.service.CallRecorderService
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.ContactUtils
import com.callrecorder.app.util.NotificationUtils

/**
 * Drives [CallRecorderService] via phone state broadcasts.
 *
 * KEY INSIGHT FOR MIUI: MIUI locks the audio hardware when a GSM call
 * connects (MODE_IN_CALL). If we try to open AudioRecord AFTER the call
 * connects (OFFHOOK), all sources fail. Starting on RINGING / NEW_OUTGOING_CALL
 * opens AudioRecord BEFORE MIUI locks it, so the session persists through the call.
 *
 * State machine:
 *  RINGING          → start recording immediately (incoming, pre-answer)
 *  NEW_OUTGOING_CALL → start recording immediately (outgoing, pre-connect)
 *  OFFHOOK          → update caller metadata only (recording already running)
 *  IDLE             → stop and save
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            Intent.ACTION_NEW_OUTGOING_CALL -> {
                val number = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER) ?: ""
                pendingOutgoingNumber = number
                isIncoming = false
                AppLogger.d(TAG, "Outgoing call to: $number — starting recorder early")

                if (!checkAutoRecord(context)) return
                startService(context,
                    phoneNumber = number,
                    callerName  = ContactUtils.resolveCallerName(context, number),
                    isIncoming  = false)
            }

            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state  = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?: pendingOutgoingNumber

                AppLogger.d(TAG, "Phone state: $state  number: $number")

                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        pendingIncomingNumber = number
                        isIncoming = true
                        AppLogger.d(TAG, "Incoming call — starting recorder early (before MIUI audio lock)")

                        if (!checkAutoRecord(context)) return
                        startService(context,
                            phoneNumber = number,
                            callerName  = ContactUtils.resolveCallerName(context, number),
                            isIncoming  = true)
                    }

                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Recording is already running from RINGING / NEW_OUTGOING_CALL.
                        // Send a second START so the service can update its caller metadata
                        // with the resolved number/name (OFFHOOK may have better info).
                        val phoneNumber = if (isIncoming) pendingIncomingNumber else pendingOutgoingNumber
                        val name = ContactUtils.resolveCallerName(context, phoneNumber)
                        NotificationUtils.sendStatusNotification(context, "Call connected — recording…")
                        AppLogger.d(TAG, "OFFHOOK — updating caller info: $phoneNumber / $name")

                        if (!checkAutoRecord(context)) return
                        startService(context,
                            phoneNumber = phoneNumber,
                            callerName  = name,
                            isIncoming  = isIncoming)
                    }

                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        NotificationUtils.sendStatusNotification(context, "Call ended — saving recording…")
                        AppLogger.d(TAG, "IDLE — stopping recorder")
                        try {
                            context.startForegroundService(
                                Intent(context, CallRecorderService::class.java).apply {
                                    action = Constants.ACTION_STOP_RECORDING
                                }
                            )
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to stop recorder service: ${e.message}")
                        }
                        pendingIncomingNumber = ""
                        pendingOutgoingNumber = ""
                        isIncoming = true
                    }
                }
            }
        }
    }

    private fun checkAutoRecord(context: Context): Boolean {
        val prefs = context.getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.PREF_AUTO_RECORD, true)
    }

    private fun startService(context: Context, phoneNumber: String, callerName: String, isIncoming: Boolean) {
        // If TelephonyManager/ContactUtils didn't supply a name or number (e.g. MIUI
        // strips EXTRA_INCOMING_NUMBER without READ_CALL_LOG), fall back to whatever
        // the notification listener captured from the dialer notification.
        val cached      = CallerNameCache.get(CallerNameCache.PHONE_CALL_PKG)
        val finalName   = callerName.ifBlank  { cached.name }
        val finalNumber = phoneNumber.ifBlank { cached.number }
        AppLogger.d(TAG, "startService name='$finalName' number='$finalNumber' incoming=$isIncoming")
        try {
            context.startForegroundService(
                Intent(context, CallRecorderService::class.java).apply {
                    action = Constants.ACTION_START_RECORDING
                    putExtra(Constants.EXTRA_PHONE_NUMBER, finalNumber)
                    putExtra(Constants.EXTRA_CALLER_NAME, finalName)
                    putExtra(Constants.EXTRA_IS_INCOMING, isIncoming)
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start recorder service: ${e.message}")
            NotificationUtils.sendStatusNotification(context, "Recorder start failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CallStateReceiver"
        private var pendingIncomingNumber = ""
        private var pendingOutgoingNumber = ""
        private var isIncoming = true
    }
}
