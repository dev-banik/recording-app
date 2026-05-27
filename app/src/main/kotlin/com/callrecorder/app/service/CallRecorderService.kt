package com.callrecorder.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.callrecorder.app.AppLogger
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.notification.CallerNameCache
import com.callrecorder.app.recorder.AudioRecorderManager
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.FileUtils
import com.callrecorder.app.util.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service that records GSM / CDMA phone calls.
 *
 * Lifecycle:
 *  ACTION_START_RECORDING → starts recording
 *  ACTION_STOP_RECORDING  → stops recording, saves metadata, stops service
 *
 * startForeground() is called at the very top of onStartCommand() before any
 * dispatching. This guarantees Android's 5-second requirement is always met,
 * even when handleStop() returns early (nothing to stop) or handleStart()
 * fails to find a working audio source.
 */
@AndroidEntryPoint
class CallRecorderService : LifecycleService() {

    @Inject lateinit var recorderManager: AudioRecorderManager
    @Inject lateinit var repository: RecordingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    private var phoneNumber  = ""
    private var callerName   = ""
    private var isIncoming   = true
    private var callStartMs  = 0L
    private var speakerEnabledByUs = false

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Call startForeground() unconditionally here — before any early returns in
        // handleStart()/handleStop(). This satisfies Android's 5-second requirement
        // regardless of what the intent action is or what happens next.
        val notification = NotificationUtils.buildRecordingNotification(
            this,
            callerName.ifBlank { phoneNumber.ifBlank { "Phone Call" } },
            CallType.PHONE.label
        )
        startForegroundCompat(Constants.NOTIF_RECORDING_ID, notification)

        when (intent?.action) {
            Constants.ACTION_START_RECORDING -> handleStart(intent)
            Constants.ACTION_STOP_RECORDING  -> handleStop()
            else -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
        return Service.START_NOT_STICKY
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundCompat(notifId: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                ServiceCompat.startForeground(this, notifId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                return
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "PHONE_CALL FGS denied: ${e.message}")
            }
            try {
                ServiceCompat.startForeground(this, notifId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                return
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "MICROPHONE FGS denied: ${e.message}")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ServiceCompat.startForeground(this, notifId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                return
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "MICROPHONE FGS denied: ${e.message}")
            }
        }
        startForeground(notifId, notification)
    }

    private fun handleStart(intent: Intent) {
        val newNumber = intent.getStringExtra(Constants.EXTRA_PHONE_NUMBER) ?: ""
        val newName   = intent.getStringExtra(Constants.EXTRA_CALLER_NAME)  ?: ""
        val newIsInc  = intent.getBooleanExtra(Constants.EXTRA_IS_INCOMING, true)

        if (recorderManager.isRecording) {
            // Second START (OFFHOOK after RINGING) — recording is already running.
            // Update metadata and optionally enable speaker mode for better capture.
            if (newNumber.isNotBlank()) phoneNumber = newNumber
            if (newName.isNotBlank())   callerName  = newName
            isIncoming = newIsInc
            AppLogger.d(TAG, "Already recording — metadata updated: $phoneNumber / $callerName")
            maybeEnableSpeaker()
            return
        }

        phoneNumber = newNumber
        callerName  = newName
        isIncoming  = newIsInc
        callStartMs = System.currentTimeMillis()

        // Update notification with caller info
        val notification = NotificationUtils.buildRecordingNotification(
            this,
            callerName.ifBlank { phoneNumber.ifBlank { "Unknown" } },
            CallType.PHONE.label
        )
        startForegroundCompat(Constants.NOTIF_RECORDING_ID, notification)

        acquireWakeLock()

        val quality = getSharedPreferences("recorder_settings", MODE_PRIVATE)
            .getInt(Constants.PREF_RECORDING_QUALITY, 1)

        val path = recorderManager.startRecording(CallType.PHONE, quality)
        if (path == null) {
            AppLogger.e(TAG, "All audio strategies failed for phone call")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            AppLogger.i(TAG, "Phone call recording started → $path")
        }
    }

    private fun handleStop() {
        val path   = recorderManager.getActivePath() ?: run {
            AppLogger.w(TAG, "handleStop: no active recording")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val source = recorderManager.getActiveStrategyName()
        val durationMs = recorderManager.stopRecording()

        // Discard very short recordings — likely a missed/rejected call with no audio
        if (durationMs < 5_000L) {
            AppLogger.i(TAG, "Recording too short (${durationMs}ms) — discarding")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        serviceScope.launch {
            // Re-check the notification cache one final time — the dialer notification
            // often arrives AFTER the RINGING handler ran, so this is the last chance
            // to pick up a name/number that appeared during the call.
            val cached = CallerNameCache.get(CallerNameCache.PHONE_CALL_PKG)
            val sizeBytes = FileUtils.fileSize(path)
            val domain = RecordingDomain(
                id              = 0,
                filePath        = path,
                fileName        = FileUtils.fileName(path),
                callerName      = callerName.ifBlank  { cached.name },
                phoneNumber     = phoneNumber.ifBlank { cached.number },
                callType        = CallType.PHONE,
                isIncoming      = isIncoming,
                timestamp       = callStartMs,
                durationMs      = durationMs,
                fileSizeBytes   = sizeBytes,
                isFavorite      = false,
                customLabel     = "",
                recordingSource = source,
            )
            repository.insert(domain)
            AppLogger.i(TAG, "Saved phone recording: $path (${durationMs}ms, source=$source)")
        }

        restoreSpeaker()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorderManager.isRecording) recorderManager.stopRecording()
        restoreSpeaker()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    /**
     * If the user enabled "Speaker mode for recording", switch the call to
     * loudspeaker at OFFHOOK so the MIC source can capture both sides.
     * Only activates when the active strategy does NOT already capture call audio
     * natively (i.e. it is not an AudioRecord(VOICE_CALL/DOWNLINK/UPLINK) strategy).
     */
    @Suppress("DEPRECATION")
    private fun maybeEnableSpeaker() {
        val prefs = getSharedPreferences("recorder_settings", MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.PREF_SPEAKER_RECORD, false)) return

        val stratName = recorderManager.getActiveStrategyName()
        val hasNativeCallCapture = "VOICE_CALL" in stratName
            || "VOICE_DOWNLINK" in stratName
            || "VOICE_UPLINK" in stratName

        if (hasNativeCallCapture) {
            AppLogger.d(TAG, "Native call-capture strategy active — speaker mode not needed")
            return
        }

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (!am.isSpeakerphoneOn) {
            am.isSpeakerphoneOn = true
            speakerEnabledByUs  = true
            AppLogger.i(TAG, "Speaker mode enabled for recording (strategy=$stratName)")
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreSpeaker() {
        if (speakerEnabledByUs) {
            (getSystemService(AUDIO_SERVICE) as AudioManager).isSpeakerphoneOn = false
            speakerEnabledByUs = false
            AppLogger.i(TAG, "Speaker mode restored")
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::RecordingWakeLock"
        ).apply { acquire(60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "CallRecorderService"
    }
}
