package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchRecordingsUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    operator fun invoke(query: String): Flow<List<RecordingDomain>> =
        repository.search(query)
}
