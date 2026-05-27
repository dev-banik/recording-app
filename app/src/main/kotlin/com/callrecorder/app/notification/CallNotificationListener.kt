package com.callrecorder.app.notification

import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.callrecorder.app.AppLogger
import com.callrecorder.app.notification.CallerNameCache.PHONE_CALL_PKG
import com.callrecorder.app.util.Constants

/**
 * Captures caller identity from incoming-call notifications for both VoIP apps
 * and the system phone dialer, storing results in [CallerNameCache].
 *
 * VoIP apps  : keyed by package name (e.g. "com.whatsapp")
 * Phone calls: keyed by [PHONE_CALL_PKG] ("__phone__")
 *
 * Title classification:
 *  - Looks like a phone number (≥7 digits, no letters) → stored as CallerInfo.number
 *  - Otherwise                                         → stored as CallerInfo.name
 *
 * This covers:
 *  ✓ Saved contact   WhatsApp/Messenger: title = "Aiub Sakib"       → name
 *  ✓ Unsaved number  WhatsApp:           title = "+8801712345678"    → number
 *  ✓ Phone call      dialer:             title = "John" or "+880..." → name or number
 *
 * Requires Notification Access granted in
 * Settings → Apps → Special app access → Notification access.
 */
class CallNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg    = sbn?.packageName ?: return
        val extras = sbn.notification?.extras ?: return
        val title  = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        if (title.isBlank()) return

        when {
            // ── VoIP apps ────────────────────────────────────────────────────
            pkg in Constants.VOIP_PACKAGES -> {
                // Skip if title is just the app's own name
                if (title.equals(Constants.VOIP_PACKAGES[pkg], ignoreCase = true)) return

                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val isCallNotif = sbn.notification.category == Notification.CATEGORY_CALL
                    || text.contains("call", ignoreCase = true)
                if (!isCallNotif) return

                val info = classify(title)
                AppLogger.d(TAG, "VoIP call from $pkg — name='${info.name}' number='${info.number}'")
                CallerNameCache.set(pkg, info)
            }

            // ── System phone dialer ──────────────────────────────────────────
            sbn.notification.category == Notification.CATEGORY_CALL -> {
                if (title.equals("unknown", ignoreCase = true)) return

                val info = classify(title)
                AppLogger.d(TAG, "Phone call notification from $pkg — name='${info.name}' number='${info.number}'")
                CallerNameCache.set(PHONE_CALL_PKG, info)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Don't clear — recording may still be saving. Next call overwrites.
    }

    companion object {
        private const val TAG = "CallNotifListener"

        private fun classify(title: String): CallerNameCache.CallerInfo =
            if (isPhoneNumber(title)) CallerNameCache.CallerInfo(name = "", number = title)
            else                      CallerNameCache.CallerInfo(name = title, number = "")

        /** True if [s] looks like a phone number: no letters, at least 7 digits. */
        private fun isPhoneNumber(s: String): Boolean {
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
