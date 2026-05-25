package com.callrecorder.app.data.db

import androidx.room.*
import com.callrecorder.app.data.db.entity.RecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE call_type = :callType ORDER BY timestamp DESC")
    fun getRecordingsByType(callType: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE is_favorite = 1 ORDER BY timestamp DESC")
    fun getFavorites(): Flow<List<RecordingEntity>>

    @Query("""
        SELECT * FROM recordings
        WHERE caller_name LIKE '%' || :query || '%'
           OR phone_number LIKE '%' || :query || '%'
           OR custom_label LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun search(query: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("SELECT COUNT(*) FROM recordings")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT SUM(file_size_bytes) FROM recordings")
    fun getTotalStorageBytes(): Flow<Long?>

    @Query("SELECT SUM(duration_ms) FROM recordings")
    fun getTotalDurationMs(): Flow<Long?>

    @Query("SELECT * FROM recordings WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getRecordingsSince(since: Long): Flow<List<RecordingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: RecordingEntity): Long

    @Update
    suspend fun update(entity: RecordingEntity)

    @Delete
    suspend fun delete(entity: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recordings WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("UPDATE recordings SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE recordings SET custom_label = :label WHERE id = :id")
    suspend fun setLabel(id: Long, label: String)

    @Query("UPDATE recordings SET duration_ms = :durationMs, file_size_bytes = :sizeBytes WHERE id = :id")
    suspend fun updateFileInfo(id: Long, durationMs: Long, sizeBytes: Long)
}
