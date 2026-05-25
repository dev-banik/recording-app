package com.callrecorder.app.ui.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.usecase.DeleteRecordingUseCase
import com.callrecorder.app.domain.usecase.GetRecordingsUseCase
import com.callrecorder.app.domain.usecase.SearchRecordingsUseCase
import com.callrecorder.app.domain.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { DATE_DESC, DATE_ASC, DURATION_DESC, DURATION_ASC, NAME_ASC }

data class RecordingsUiState(
    val recordings: List<RecordingDomain> = emptyList(),
    val isLoading: Boolean = false,
    val filter: String? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val searchQuery: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val getRecordings: GetRecordingsUseCase,
    private val searchRecordings: SearchRecordingsUseCase,
    private val deleteRecording: DeleteRecordingUseCase,
    private val repository: RecordingRepository,
) : ViewModel() {

    private val _filter     = MutableStateFlow<String?>(null)
    private val _sortOrder  = MutableStateFlow(SortOrder.DATE_DESC)
    private val _query      = MutableStateFlow("")

    val uiState: StateFlow<RecordingsUiState> = combine(
        _filter, _sortOrder, _query
    ) { filter, sort, query -> Triple(filter, sort, query) }
        .flatMapLatest { (filter, sort, query) ->
            val source = if (query.length >= 2) searchRecordings(query)
                         else getRecordings(filter)
            source.map { list ->
                RecordingsUiState(
                    recordings  = list.sorted(sort),
                    filter      = filter,
                    sortOrder   = sort,
                    searchQuery = query,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUiState(isLoading = true))

    fun setFilter(callType: String?)  { _filter.value    = callType }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setQuery(q: String)            { _query.value     = q }

    fun delete(recording: RecordingDomain) {
        viewModelScope.launch {
            deleteRecording(recording.id, recording.filePath)
        }
    }

    fun toggleFavorite(recording: RecordingDomain) {
        viewModelScope.launch {
            repository.setFavorite(recording.id, !recording.isFavorite)
        }
    }

    fun rename(recording: RecordingDomain, newLabel: String) {
        viewModelScope.launch {
            repository.setLabel(recording.id, newLabel)
        }
    }

    private fun List<RecordingDomain>.sorted(order: SortOrder) = when (order) {
        SortOrder.DATE_DESC     -> sortedByDescending { it.timestamp }
        SortOrder.DATE_ASC      -> sortedBy { it.timestamp }
        SortOrder.DURATION_DESC -> sortedByDescending { it.durationMs }
        SortOrder.DURATION_ASC  -> sortedBy { it.durationMs }
        SortOrder.NAME_ASC      -> sortedBy { it.displayName }
    }
}
