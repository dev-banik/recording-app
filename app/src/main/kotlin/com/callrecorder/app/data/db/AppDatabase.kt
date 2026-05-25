package com.callrecorder.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.callrecorder.app.data.db.entity.RecordingEntity

@Database(
    entities = [RecordingEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        const val DATABASE_NAME = "call_recorder_db"
    }
}
