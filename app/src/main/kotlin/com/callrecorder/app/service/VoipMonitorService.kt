package com.callrecorder.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
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

    @SuppressLint("InlinedApi")
    private fun handleVoipStart(intent: Intent) {
        val packageName = intent.getStringExtra(Constants.EXTRA_CALL_TYPE) ?: ""
        activeCallType  = CallType.fromPackage(packageName)
        callStartMs     = System.currentTimeMillis()

        val appName = Constants.VOIP_PACKAGES[packageName] ?: "VoIP"
        val notification = NotificationUtils.buildRecordingNotification(
            this, appName, activeCallType.label
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                ServiceCompat.startForeground(this, Constants.NOTIF_VOIP_ID, notification, fgsType)
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "FGS type=$fgsType denied, trying MICROPHONE fallback: ${e.message}")
                try {
                    ServiceCompat.startForeground(
                        this, Constants.NOTIF_VOIP_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } catch (e2: SecurityException) {
                    AppLogger.e(TAG, "All FGS types denied: ${e2.message}")
                    NotificationUtils.sendStatusNotification(this, "VoIP recording blocked — check permissions")
                    stopSelf()
                    return
                }
            }
        } else {
            startForeground(Constants.NOTIF_VOIP_ID, notification)
        }

        val quality = getSharedPreferences("recorder_settings", MODE_PRIVATE)
            .getInt(Constants.PREF_RECORDING_QUALITY, 1)

        recorderManager.startRecording(activeCallType, quality)
        AppLogger.i(TAG, "VoIP recording started for $appName")
    }

    private fun handleVoipStop() {
        if (!recorderManager.isRecording) { stopSelf(); return }

        // Read path/source BEFORE stopRecording() clears them
        val path   = recorderManager.getActivePath() ?: run { stopSelf(); return }
        val source = recorderManager.getActiveStrategyName()
        val durationMs = recorderManager.stopRecording()

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
