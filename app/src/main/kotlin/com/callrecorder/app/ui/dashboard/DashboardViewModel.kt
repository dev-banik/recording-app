package com.callrecorder.app.ui.dashboard

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.recorder.AudioRecorderManager
import com.callrecorder.app.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val totalRecordings: Int = 0,
    val totalDurationMs: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val isTestRecording: Boolean = false,
    val statusMessage: String = "Monitoring active",
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecordingRepository,
    private val recorderManager: AudioRecorderManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _testRecording = MutableStateFlow(false)
    private val _statusMessage = MutableStateFlow(buildStatusMessage())

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getTotalCount(),
        repository.getTotalDurationMs(),
        repository.getTotalStorageBytes(),
        _testRecording,
        _statusMessage,
    ) { count, duration, storage, testing, status ->
        DashboardUiState(
            totalRecordings  = count,
            totalDurationMs  = duration,
            storageUsedBytes = storage,
            isTestRecording  = testing,
            statusMessage    = status,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun refreshStatus() {
        if (!_testRecording.value) _statusMessage.value = buildStatusMessage()
    }

    private fun buildStatusMessage(): String {
        val a11yEnabled = isAccessibilityServiceEnabled()
        return if (a11yEnabled) {
            "Monitoring active  |  VoIP detection: ON"
        } else {
            "Phone calls: ON  |  WhatsApp/VoIP: OFF\n-> Enable in Settings > Accessibility > Call Recorder"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return flat.split(":").any { it.contains(context.packageName, ignoreCase = true) }
    }

    fun toggleTestRecording() {
        if (recorderManager.isRecording) {
            val path   = recorderManager.getActivePath()
            val source = recorderManager.getActiveStrategyName()
            val durationMs = recorderManager.stopRecording()
            _testRecording.value = false

            if (path != null) {
                viewModelScope.launch {
                    repository.insert(
                        RecordingDomain(
                            id              = 0,
                            filePath        = path,
                            fileName        = FileUtils.fileName(path),
                            callerName      = "Manual Test",
                            phoneNumber     = "",
                            callType        = CallType.PHONE,
                            isIncoming      = false,
                            timestamp       = System.currentTimeMillis(),
                            durationMs      = durationMs,
                            fileSizeBytes   = FileUtils.fileSize(path),
                            isFavorite      = false,
                            customLabel     = "mic-test",
                            recordingSource = source,
                        )
                    )
                    _statusMessage.value = "Test saved ✓  source: $source"
                }
            } else {
                _statusMessage.value = "Test FAILED — mic permission denied or hardware busy"
            }
        } else {
            val path = recorderManager.startRecording(CallType.PHONE, 1)
            if (path != null) {
                _testRecording.value = true
                _statusMessage.value = "Recording mic… tap Stop when done"
            } else {
                _statusMessage.value = "FAILED — all audio sources rejected. Check mic permission."
            }
        }
    }
}
