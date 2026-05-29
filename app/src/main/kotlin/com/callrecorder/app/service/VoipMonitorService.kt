package com.callrecorder.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
 * Foreground service that records VoIP calls (WhatsApp, Telegram, etc.).
 *
 * startForeground() is called at the very top of onStartCommand() before any
 * dispatching — same pattern as CallRecorderService — to guarantee the
 * 5-second Android requirement is always met.
 */
@AndroidEntryPoint
class VoipMonitorService : LifecycleService() {

    @Inject lateinit var recorderManager: AudioRecorderManager
    @Inject lateinit var repository: RecordingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeCallType    = CallType.VOIP
    private var activePackageName = ""
    private var callerName        = ""
    private var phoneNumber       = ""
    private var callStartMs       = 0L
    private var pendingStartJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationUtils.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Call startForeground() unconditionally first — satisfies Android's 5-second
        // requirement even if handleVoipStop() returns early (nothing to stop).
        val notification = NotificationUtils.buildRecordingNotification(
            this, "VoIP Call", activeCallType.label
        )
        startForegroundCompat(Constants.NOTIF_VOIP_ID, notification)

        when (intent?.action) {
            Constants.ACTION_START_VOIP -> handleVoipStart(intent)
            Constants.ACTION_STOP_VOIP  -> handleVoipStop()
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

    private fun handleVoipStart(intent: Intent) {
        val packageName = intent.getStringExtra(Constants.EXTRA_CALL_TYPE) ?: ""

        if (recorderManager.isRecording) {
            // Second START (e.g. accessibility + phone-state both fired for the same call).
            // Just update metadata — don't open a second AudioRecord session.
            val name = intent.getStringExtra(Constants.EXTRA_CALLER_NAME) ?: ""
            val num  = intent.getStringExtra(Constants.EXTRA_PHONE_NUMBER) ?: ""
            if (name.isNotBlank()) callerName  = name
            if (num.isNotBlank())  phoneNumber = num
            AppLogger.d(TAG, "Already recording — metadata updated for $packageName")
            return
        }

        activePackageName = packageName
        activeCallType    = CallType.fromPackage(packageName)
        callerName      = intent.getStringExtra(Constants.EXTRA_CALLER_NAME) ?: ""
        phoneNumber     = intent.getStringExtra(Constants.EXTRA_PHONE_NUMBER) ?: ""
        callStartMs     = System.currentTimeMillis()

        val appName = Constants.VOIP_PACKAGES[packageName] ?: "VoIP"

        // Update notification with actual app name
        val notification = NotificationUtils.buildRecordingNotification(
            this, appName, activeCallType.label
        )
        startForegroundCompat(Constants.NOTIF_VOIP_ID, notification)

        val quality = getSharedPreferences("recorder_settings", MODE_PRIVATE)
            .getInt(Constants.PREF_RECORDING_QUALITY, 1)

        // Delay 1.5 s so the VoIP app's audio pipeline is fully established
        // before we open any audio source alongside it.
        pendingStartJob = serviceScope.launch {
            delay(1500)
            val path = recorderManager.startRecording(activeCallType, quality)
            if (path != null) {
                AppLogger.i(TAG, "VoIP recording started for $appName → $path")
            } else {
                AppLogger.e(TAG, "All VoIP audio strategies failed for $appName")
            }
        }
    }

    private fun handleVoipStop() {
        pendingStartJob?.cancel()
        pendingStartJob = null

        if (!recorderManager.isRecording) {
            AppLogger.w(TAG, "handleVoipStop: no active recording")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val path   = recorderManager.getActivePath() ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val source = recorderManager.getActiveStrategyName()
        val durationMs = recorderManager.stopRecording()

        serviceScope.launch {
            val cached = CallerNameCache.get(activePackageName)
            val domain = RecordingDomain(
                id              = 0,
                filePath        = path,
                fileName        = FileUtils.fileName(path),
                callerName      = callerName.ifBlank  { cached.name },
                phoneNumber     = phoneNumber.ifBlank { cached.number },
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
            AppLogger.i(TAG, "VoIP recording saved: $path caller=$callerName (source=$source)")
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
