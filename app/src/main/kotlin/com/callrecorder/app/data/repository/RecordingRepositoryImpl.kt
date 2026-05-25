package com.callrecorder.app.data.repository

import com.callrecorder.app.data.db.RecordingDao
import com.callrecorder.app.data.db.entity.RecordingEntity
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RecordingRepositoryImpl @Inject constructor(
    private val dao: RecordingDao
) : RecordingRepository {

    override fun getAllRecordings(): Flow<List<RecordingDomain>> =
        dao.getAllRecordings().map { list -> list.map { it.toDomain() } }

    override fun getRecordingsByType(callType: String): Flow<List<RecordingDomain>> =
        dao.getRecordingsByType(callType).map { list -> list.map { it.toDomain() } }

    override fun getFavorites(): Flow<List<RecordingDomain>> =
        dao.getFavorites().map { list -> list.map { it.toDomain() } }

    override fun search(query: String): Flow<List<RecordingDomain>> =
        dao.search(query).map { list -> list.map { it.toDomain() } }

    override fun getTotalCount(): Flow<Int> = dao.getTotalCount()

    override fun getTotalStorageBytes(): Flow<Long> =
        dao.getTotalStorageBytes().map { it ?: 0L }

    override fun getTotalDurationMs(): Flow<Long> =
        dao.getTotalDurationMs().map { it ?: 0L }

    override suspend fun getById(id: Long): RecordingDomain? =
        dao.getById(id)?.toDomain()

    override suspend fun insert(recording: RecordingDomain): Long =
        dao.insert(recording.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun deleteOlderThan(timestampMs: Long): Int =
        dao.deleteOlderThan(timestampMs)

    override suspend fun setFavorite(id: Long, favorite: Boolean) =
        dao.setFavorite(id, favorite)

    override suspend fun setLabel(id: Long, label: String) =
        dao.setLabel(id, label)

    override suspend fun updateFileInfo(id: Long, durationMs: Long, sizeBytes: Long) =
        dao.updateFileInfo(id, durationMs, sizeBytes)

    // ── Mappers ───────────────────────────────────────────────────────────

    private fun RecordingEntity.toDomain() = RecordingDomain(
        id              = id,
        filePath        = filePath,
        fileName        = fileName,
        callerName      = callerName,
        phoneNumber     = phoneNumber,
        callType        = CallType.fromString(callType),
        isIncoming      = isIncoming,
        timestamp       = timestamp,
        durationMs      = durationMs,
        fileSizeBytes   = fileSizeBytes,
        isFavorite      = isFavorite,
        customLabel     = customLabel,
        recordingSource = recordingSource,
    )

    private fun RecordingDomain.toEntity() = RecordingEntity(
        id              = id,
        filePath        = filePath,
        fileName        = fileName,
        callerName      = callerName,
        phoneNumber     = phoneNumber,
        callType        = callType.name,
        isIncoming      = isIncoming,
        timestamp       = timestamp,
        durationMs      = durationMs,
        fileSizeBytes   = fileSizeBytes,
        isFavorite      = isFavorite,
        customLabel     = customLabel,
        recordingSource = recordingSource,
    )
}
