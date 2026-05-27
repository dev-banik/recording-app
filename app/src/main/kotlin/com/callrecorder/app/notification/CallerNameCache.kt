package com.callrecorder.app.notification

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime cache: maps VoIP package name → [CallerInfo] extracted from
 * the incoming-call notification. Written by [CallNotificationListener],
 * read by [com.callrecorder.app.accessibility.CallMonitorAccessibilityService].
 */
object CallerNameCache {

    data class CallerInfo(val name: String, val number: String)

    private val map = ConcurrentHashMap<String, CallerInfo>()

    fun set(pkg: String, info: CallerInfo) { map[pkg] = info }
    fun get(pkg: String): CallerInfo = map[pkg] ?: CallerInfo("", "")
}
