package com.callrecorder.app.domain.usecase

import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.util.FileUtils
import javax.inject.Inject

class DeleteRecordingUseCase @Inject constructor(
    private val repository: RecordingRepository
) {
    suspend operator fun invoke(id: Long, filePath: String) {
        FileUtils.deleteFile(filePath)
        repository.delete(id)
    }
}
