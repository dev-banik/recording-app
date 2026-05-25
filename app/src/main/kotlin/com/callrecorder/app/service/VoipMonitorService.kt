package com.callrecorder.app.service

import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import com.callrecorder.app.AppLogger
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.recorder.AudioRecorderManager
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.FileUtils
import com.callrecorder.app.util.NotificationUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Foreground service that records VoIP calls (WhatsApp, Telegram, etc.).
 *
 * Detection is driven by [CallMonitorAccessibilityService] which monitors
 * foreground app changes and audio mode transitions.
 *
 * Recording strategy priority:
 *  1. AudioPlaybackCapture (Android 10+, if MediaProjection granted)
 *  2. VOICE_COMMUNICATION audio source (captures mic; some ROMs do both)
 *  3. MIC fallback
 */
@AndroidEntryPoint
class VoipMonitorService : LifecycleService() {

    @Inject lateinit var recorderManager: AudioRecorderManager
    @Inject lateinit var repository: RecordingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeCallType = CallType.VOIP
    private var callStartMs    = 0L

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            Constants.ACTION_START_VOIP -> handleVoipStart(intent)
            Constants.ACTION_STOP_VOIP  -> handleVoipStop()
        }
        return Service.START_STICKY
    }

    private fun handleVoipStart(intent: Intent) {
        val packageName = intent.getStringExtra(Constants.EXTRA_CALL_TYPE) ?: ""
        activeCallType  = CallType.fromPackage(packageName)
        callStartMs     = System.currentTimeMillis()

        val appName = Constants.VOIP_PACKAGES[packageName] ?: "VoIP"
        val notification = NotificationUtils.buildRecordingNotification(
            this, appName, activeCallType.label
        )
        startForeground(Constants.NOTIF_VOIP_ID, notification)

        val quality = getSharedPreferences("recorder_settings", MODE_PRIVATE)
            .getInt(Constants.PREF_RECORDING_QUALITY, 1)

        recorderManager.startRecording(activeCallType, quality)
        AppLogger.i(TAG, "VoIP recording started for $appName")
    }

    private fun handleVoipStop() {
        if (!recorderManager.isRecording) { stopSelf(); return }

        val durationMs = recorderManager.stopRecording()
        val path       = recorderManager.getActivePath() ?: run { stopSelf(); return }
        val source     = recorderManager.getActiveStrategyName()

        serviceScope.launch {
            val domain = RecordingDomain(
                id              = 0,
                filePath        = path,
                fileName        = FileUtils.fileName(path),
                callerName      = "",
                phoneNumber     = "",
                callType        = activeCallType,
                isIncoming      = true,
                timestamp       = callStartMs,
                durationMs      = durationMs,
                fileSizeBytes   = FileUtils.fileSize(path),
                isFavorite      = false,
                customLabel     = "",
                recordingSource = source,
            )
            repository.insert(domain)
            AppLogger.i(TAG, "VoIP recording saved: $path")
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorderManager.isRecording) recorderManager.stopRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        private const val TAG = "VoipMonitorService"
    }
}
