package com.callrecorder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callrecorder.app.AppLogger

/**
 * Restarts call monitoring after device reboot.
 * The [CallStateReceiver] is a manifest receiver so it auto-wakes;
 * this receiver handles cases where additional initialisation is needed.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        if (intent.action !in validActions) return

        AppLogger.i(TAG, "Boot received — app will auto-respond to calls via CallStateReceiver")
        // No explicit service needed — CallStateReceiver is registered in manifest
        // and will receive ACTION_PHONE_STATE_CHANGED automatically.
        // If the app had a persistent monitoring service, start it here.
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
