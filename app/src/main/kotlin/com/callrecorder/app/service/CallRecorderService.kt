package com.callrecorder.app.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
 * Foreground service that records GSM / CDMA phone calls.
 *
 * Lifecycle:
 *  ACTION_START_RECORDING → starts foreground + recording
 *  ACTION_STOP_RECORDING  → stops recording, saves metadata, stops service
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

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            Constants.ACTION_START_RECORDING -> handleStart(intent)
            Constants.ACTION_STOP_RECORDING  -> handleStop()
        }
        return Service.START_STICKY
    }

    @SuppressLint("InlinedApi")
    private fun handleStart(intent: Intent) {
        phoneNumber = intent.getStringExtra(Constants.EXTRA_PHONE_NUMBER) ?: ""
        callerName  = intent.getStringExtra(Constants.EXTRA_CALLER_NAME)  ?: ""
        isIncoming  = intent.getBooleanExtra(Constants.EXTRA_IS_INCOMING, true)
        callStartMs = System.currentTimeMillis()

        val notification = NotificationUtils.buildRecordingNotification(
            this,
            callerName.ifBlank { phoneNumber.ifBlank { "Unknown" } },
            CallType.PHONE.label
        )

        // Android 14+: PHONE_CALL type is allowed from background when a call is active.
        // API 30-33: MICROPHONE type has no background-start restrictions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                ServiceCompat.startForeground(this, Constants.NOTIF_RECORDING_ID, notification, fgsType)
            } catch (e: SecurityException) {
                AppLogger.w(TAG, "FGS type=$fgsType denied, using basic startForeground: ${e.message}")
                startForeground(Constants.NOTIF_RECORDING_ID, notification)
            }
        } else {
            startForeground(Constants.NOTIF_RECORDING_ID, notification)
        }

        acquireWakeLock()

        val quality = getSharedPreferences("recorder_settings", MODE_PRIVATE)
            .getInt(Constants.PREF_RECORDING_QUALITY, 1)

        val path = recorderManager.startRecording(CallType.PHONE, quality)
        if (path == null) {
            AppLogger.e(TAG, "Failed to start recording — stopping service")
            stopSelf()
        }
    }

    private fun handleStop() {
        val durationMs = recorderManager.stopRecording()
        val path       = recorderManager.getActivePath() ?: run { stopSelf(); return }
        val source     = recorderManager.getActiveStrategyName()

        serviceScope.launch {
            val sizeBytes = FileUtils.fileSize(path)
            val domain = RecordingDomain(
                id              = 0,
                filePath        = path,
                fileName        = FileUtils.fileName(path),
                callerName      = callerName,
                phoneNumber     = phoneNumber,
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
            AppLogger.i(TAG, "Saved recording: $path (${durationMs}ms, ${sizeBytes}B)")
        }

        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorderManager.isRecording) recorderManager.stopRecording()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG::RecordingWakeLock"
        ).apply { acquire(60 * 60 * 1000L /* 1 hour max */) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        private const val TAG = "CallRecorderService"
    }
}
