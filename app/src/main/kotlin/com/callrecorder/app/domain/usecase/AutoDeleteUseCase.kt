package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.repository.RecordingRepository
import javax.inject.Inject

class AutoDeleteUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    suspend operator fun invoke(keepDays: Int): Int {
        if (keepDays <= 0) return 0
        val cutoff = System.currentTimeMillis() - (keepDays * 24 * 60 * 60 * 1000L)
        return repository.deleteOlderThan(cutoff)
    }
}
