package com.callrecorder.app.domain.repository

import com.callrecorder.app.domain.model.RecordingDomain
import kotlinx.coroutines.flow.Flow

interface RecordingRepository {

    fun getAllRecordings(): Flow<List<RecordingDomain>>

    fun getRecordingsByType(callType: String): Flow<List<RecordingDomain>>

    fun getFavorites(): Flow<List<RecordingDomain>>

    fun search(query: String): Flow<List<RecordingDomain>>

    fun getTotalCount(): Flow<Int>

    fun getTotalStorageBytes(): Flow<Long>

    fun getTotalDurationMs(): Flow<Long>

    suspend fun getById(id: Long): RecordingDomain?

    suspend fun insert(recording: RecordingDomain): Long

    suspend fun delete(id: Long)

    suspend fun deleteOlderThan(timestampMs: Long): Int

    suspend fun setFavorite(id: Long, favorite: Boolean)

    suspend fun setLabel(id: Long, label: String)

    suspend fun updateFileInfo(id: Long, durationMs: Long, sizeBytes: Long)
}
