package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecordingsUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    operator fun invoke(callType: String? = null): Flow<List<RecordingDomain>> = when {
        callType == "FAVORITES"   -> repository.getFavorites()
        callType.isNullOrBlank()  -> repository.getAllRecordings()
        else                      -> repository.getRecordingsByType(callType)
    }
}
