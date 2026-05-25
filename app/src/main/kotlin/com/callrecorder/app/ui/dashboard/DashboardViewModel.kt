package com.callrecorder.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardUiState(
    val totalRecordings: Int = 0,
    val totalDurationMs: Long = 0L,
    val storageUsedBytes: Long = 0L,
    val recentCount: Int = 0,         // recordings in last 7 days
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getTotalCount(),
        repository.getTotalDurationMs(),
        repository.getTotalStorageBytes()
    ) { count, duration, storage ->
        DashboardUiState(
            totalRecordings  = count,
            totalDurationMs  = duration,
            storageUsedBytes = storage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}
