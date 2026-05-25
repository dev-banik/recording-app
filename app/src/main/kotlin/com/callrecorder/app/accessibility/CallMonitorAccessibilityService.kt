package com.callrecorder.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent
import com.callrecorder.app.AppLogger
import com.callrecorder.app.service.VoipMonitorService
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.NotificationUtils

/**
 * Accessibility service for VoIP call detection.
 *
 * Monitors foreground app changes to detect when a known VoIP application
 * becomes active. Combines foreground-app detection with AudioManager mode
 * changes (MODE_IN_COMMUNICATION) to determine call start/end reliably.
 *
 * Why accessibility is needed:
 *  - No broadcast is fired for VoIP call state changes.
 *  - AudioManager mode change alone has false positives.
 *  - Combining both gives high confidence of an active VoIP call.
 *
 * Setup: User must grant accessibility permission in Android settings.
 */
class CallMonitorAccessibilityService : AccessibilityService() {

    private var currentVoipPackage: String? = null
    private var isVoipCallActive = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            if (pkg in Constants.VOIP_PACKAGES) {
                handleVoipAppForeground(pkg)
            } else if (currentVoipPackage != null && pkg !in Constants.VOIP_PACKAGES) {
                // VoIP app left foreground — call likely ended
                maybeStopVoipRecording()
            }
        }
    }

    private fun handleVoipAppForeground(packageName: String) {
        currentVoipPackage = packageName
        val prefs = getSharedPreferences("recorder_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.PREF_RECORD_VOIP, true)) return

        // Poll audio mode; MODE_IN_COMMUNICATION confirms active call
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (audioManager.mode == AudioManager.MODE_IN_COMMUNICATION && !isVoipCallActive) {
            startVoipRecording(packageName)
        } else if (audioManager.mode != AudioManager.MODE_IN_COMMUNICATION && isVoipCallActive) {
            maybeStopVoipRecording()
        }
    }

    private fun startVoipRecording(packageName: String) {
        isVoipCallActive = true
        val appName = Constants.VOIP_PACKAGES[packageName] ?: packageName
        AppLogger.i(TAG, "VoIP call detected: $packageName — starting recording")
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
            NotificationUtils.sendStatusNotification(this, "VoIP recorder failed to start: ${e.message}")
        }
    }

    private fun maybeStopVoipRecording() {
        if (!isVoipCallActive) return
        isVoipCallActive = false
        currentVoipPackage = null
        AppLogger.i(TAG, "VoIP call ended — stopping recording")
        NotificationUtils.sendStatusNotification(this, "VoIP call ended — saving recording…")
        try {
            startForegroundService(
                Intent(this, VoipMonitorService::class.java).apply {
                    action = Constants.ACTION_STOP_VOIP
                }
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop VoIP recorder: ${e.message}")
        }
    }

    override fun onInterrupt() {
        AppLogger.w(TAG, "Accessibility service interrupted")
        if (isVoipCallActive) maybeStopVoipRecording()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.i(TAG, "Accessibility service connected")
    }

    companion object {
        private const val TAG = "CallMonitorA11y"
    }
}
