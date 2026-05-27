package com.callrecorder.app.notification

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-lifetime cache: maps VoIP package name → caller name extracted from
 * the incoming-call notification. Written by [CallNotificationListener],
 * read by [com.callrecorder.app.accessibility.CallMonitorAccessibilityService].
 */
object CallerNameCache {
    private val map = ConcurrentHashMap<String, String>()

    fun set(pkg: String, name: String) { map[pkg] = name }
    fun get(pkg: String): String = map[pkg] ?: ""
    fun clear(pkg: String) { map.remove(pkg) }
}
