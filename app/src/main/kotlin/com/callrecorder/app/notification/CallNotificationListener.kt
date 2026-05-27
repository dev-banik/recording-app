package com.callrecorder.app.notification

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.callrecorder.app.AppLogger
import com.callrecorder.app.util.Constants

/**
 * Listens for notifications from VoIP apps and extracts the caller name from
 * the incoming-call notification title. The name is stored in [CallerNameCache]
 * so [com.callrecorder.app.accessibility.CallMonitorAccessibilityService] can
 * attach it to the recording when the call is detected.
 *
 * Requires the user to grant Notification Access in
 * Settings → Apps → Special app access → Notification access.
 */
class CallNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg   = sbn?.packageName ?: return
        if (pkg !in Constants.VOIP_PACKAGES) return

        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        if (title.isBlank()) return

        // Ignore if the title is just the app's own name rather than a contact name
        if (title.equals(Constants.VOIP_PACKAGES[pkg], ignoreCase = true)) return

        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val isCallNotif = sbn.notification.category == Notification.CATEGORY_CALL
            || text.contains("call", ignoreCase = true)

        if (isCallNotif) {
            AppLogger.d(TAG, "Incoming call from $pkg — caller: $title")
            CallerNameCache.set(pkg, title)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Don't clear on removal — the recording may still be in progress.
        // CallerNameCache entries are short-lived (overwritten by the next call).
    }

    companion object {
        private const val TAG = "CallNotifListener"

        fun isGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return flat.contains(context.packageName)
        }
    }
}
