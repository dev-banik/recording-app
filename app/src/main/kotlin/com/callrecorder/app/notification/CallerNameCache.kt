package com.callrecorder.app.notification

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime cache mapping a key → [CallerInfo].
 *
 * Keys:
 *  - A VoIP package name (e.g. "com.whatsapp") for VoIP calls
 *  - [PHONE_CALL_PKG] for GSM/CDMA phone calls
 *
 * Written by [CallNotificationListener], read by
 * [com.callrecorder.app.accessibility.CallMonitorAccessibilityService] (VoIP)
 * and [com.callrecorder.app.receiver.CallStateReceiver] (phone calls).
 */
object CallerNameCache {

    /** Sentinel key used for system phone-call notifications. */
    const val PHONE_CALL_PKG = "__phone__"

    data class CallerInfo(val name: String, val number: String)

    private val map = ConcurrentHashMap<String, CallerInfo>()

    fun set(pkg: String, info: CallerInfo) { map[pkg] = info }
    fun get(pkg: String): CallerInfo = map[pkg] ?: CallerInfo("", "")

    /**
     * Set by [com.callrecorder.app.notification.CallNotificationListener] when a VoIP app
     * posts a CATEGORY_CALL notification. On MIUI, apps like Messenger route their calls
     * through Android's ConnectionService API which fires telephony state changes (RINGING
     * etc.). [com.callrecorder.app.receiver.CallStateReceiver] reads this flag to detect
     * such calls and route them to VoipMonitorService instead of CallRecorderService.
     */
    @Volatile var pendingVoipPackage: String? = null
}
