package com.callrecorder.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.callrecorder.app.AppLogger
import com.callrecorder.app.service.VoipMonitorService
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.NotificationUtils

/**
 * Detects VoIP calls by watching which app is in the foreground combined
 * with periodic audio-mode polling.
 *
 * Why polling is needed:
 *   The window-state event fires when WhatsApp enters foreground, but at
 *   that moment the call hasn't connected yet — AudioManager.mode is still
 *   MODE_NORMAL. We poll every 2 s so we catch the moment it switches to
 *   MODE_IN_COMMUNICATION regardless of when the UI event arrived.
 */
class CallMonitorAccessibilityService : AccessibilityService() {

    private var currentVoipPackage: String? = null
    private var isVoipCallActive = false

    private val handler = Handler(Looper.getMainLooper())
    private val audioModePoller = object : Runnable {
        override fun run() {
            pollAudioMode()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // ── Accessibility callbacks ──────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        if (pkg in Constants.VOIP_PACKAGES) {
            if (currentVoipPackage != pkg) {
                AppLogger.d(TAG, "VoIP app foreground: $pkg")
                currentVoipPackage = pkg
                startPolling()
            }
        } else if (currentVoipPackage != null) {
            // User switched away from the VoIP app
            AppLogger.d(TAG, "VoIP app left foreground ($currentVoipPackage → $pkg)")
            currentVoipPackage = null
            stopPolling()
            if (isVoipCallActive) maybeStopVoipRecording()
        }
    }

    override fun onInterrupt() {
        AppLogger.w(TAG, "Accessibility service interrupted")
        stopPolling()
        if (isVoipCallActive) maybeStopVoipRecording()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.i(TAG, "Accessibility service connected")
        NotificationUtils.sendStatusNotification(this, "Call Recorder accessibility service active")
    }

    override fun onDestroy() {
        stopPolling()
        super.onDestroy()
    }

    // ── Polling ──────────────────────────────────────────────────────────────

    private fun startPolling() {
        handler.removeCallbacks(audioModePoller)
        handler.post(audioModePoller)
    }

    private fun stopPolling() {
        handler.removeCallbacks(audioModePoller)
    }

    private fun pollAudioMode() {
        val pkg = currentVoipPackage ?: return
        val prefs = getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.PREF_RECORD_VOIP, true)) return

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        val inCall = am.mode == AudioManager.MODE_IN_COMMUNICATION

        when {
            inCall && !isVoipCallActive -> {
                AppLogger.i(TAG, "Audio mode IN_COMMUNICATION — starting VoIP recording for $pkg")
                startVoipRecording(pkg)
            }
            !inCall && isVoipCallActive -> {
                AppLogger.i(TAG, "Audio mode left IN_COMMUNICATION — stopping VoIP recording")
                maybeStopVoipRecording()
            }
        }
    }

    // ── Recording control ────────────────────────────────────────────────────

    private fun startVoipRecording(packageName: String) {
        isVoipCallActive = true
        val appName = Constants.VOIP_PACKAGES[packageName] ?: packageName
        AppLogger.i(TAG, "VoIP recording starting for $appName")
        NotificationUtils.sendStatusNotification(this, "$appName call detected — starting recorder…")
        try {
            startForegroundService(
                Intent(this, VoipMonitorService::class.java).apply {
                    action = Constants.ACTION_START_VOIP
                    putExtra(Constants.EXTRA_CALL_TYPE, packageName)
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start VoIP recorder: ${e.message}")
            NotificationUtils.sendStatusNotification(this, "VoIP recorder failed: ${e.message}")
            isVoipCallActive = false
        }
    }

    private fun maybeStopVoipRecording() {
        if (!isVoipCallActive) return
        isVoipCallActive = false
        AppLogger.i(TAG, "VoIP call ended — stopping recorder")
        NotificationUtils.sendStatusNotification(this, "VoIP call ended — saving recording…")
        try {
            // startForeground() is called at the top of VoipMonitorService.onStartCommand()
            // before dispatching, so the 5-second requirement is always met for STOP too.
            startForegroundService(
                Intent(this, VoipMonitorService::class.java).apply {
                    action = Constants.ACTION_STOP_VOIP
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop VoIP recorder: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CallMonitorA11y"
        private const val POLL_INTERVAL_MS = 2000L
    }
}
