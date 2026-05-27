package com.callrecorder.app.notification

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.callrecorder.app.AppLogger
import com.callrecorder.app.util.Constants

/**
 * Listens for notifications from VoIP apps and extracts the caller identity
 * from the incoming-call notification title.
 *
 * If the title looks like a contact name  → stored as callerName.
 * If the title looks like a phone number  → stored as phoneNumber.
 *
 * This covers both cases:
 *  - Saved contact  : WhatsApp shows "Aiub Sakib"    → callerName = "Aiub Sakib"
 *  - Unsaved number : WhatsApp shows "+8801712345678" → phoneNumber = "+8801712345678"
 *
 * Requires the user to grant Notification Access in
 * Settings → Apps → Special app access → Notification access.
 */
class CallNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg !in Constants.VOIP_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        if (title.isBlank()) return

        // Skip if the title is literally the app's own name
        if (title.equals(Constants.VOIP_PACKAGES[pkg], ignoreCase = true)) return

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val isCallNotif = sbn.notification.category == Notification.CATEGORY_CALL
            || text.contains("call", ignoreCase = true)

        if (!isCallNotif) return

        val info = if (isPhoneNumber(title)) {
            AppLogger.d(TAG, "Call from $pkg — number: $title")
            CallerNameCache.CallerInfo(name = "", number = title)
        } else {
            AppLogger.d(TAG, "Call from $pkg — name: $title")
            CallerNameCache.CallerInfo(name = title, number = "")
        }
        CallerNameCache.set(pkg, info)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Don't clear — recording may still be saving. Next call overwrites.
    }

    companion object {
        private const val TAG = "CallNotifListener"

        /** Returns true if [s] looks like a phone number rather than a person's name. */
        private fun isPhoneNumber(s: String): Boolean {
            // Must have no letters and at least 7 digit characters
            if (s.any { it.isLetter() }) return false
            return s.count { it.isDigit() } >= 7
        }

        fun isGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(context.packageName)
        }
    }
}
