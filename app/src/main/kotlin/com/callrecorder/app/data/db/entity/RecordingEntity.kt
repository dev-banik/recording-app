package com.callrecorder.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recordings",
    indices = [Index("timestamp"), Index("call_type"), Index("phone_number")]
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "caller_name")
    val callerName: String = "",

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String = "",

    @ColumnInfo(name = "call_type")
    val callType: String,          // "PHONE", "WHATSAPP", "MESSENGER", "TELEGRAM", etc.

    @ColumnInfo(name = "is_incoming")
    val isIncoming: Boolean = true,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,           // Unix epoch millis — call START time

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0,

    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long = 0,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "custom_label")
    val customLabel: String = "",

    @ColumnInfo(name = "recording_source")
    val recordingSource: String = "", // which strategy was used

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
