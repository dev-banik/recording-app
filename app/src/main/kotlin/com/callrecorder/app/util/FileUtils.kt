package com.callrecorder.app.util

import android.content.Context
import com.callrecorder.app.domain.model.CallType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    /**
     * Creates the output file path for a new recording.
     * Directory is created if absent. File is NOT yet written.
     */
    fun createOutputFile(context: Context, callType: CallType): String {
        val dir = getRecordingDir(context, callType)
        dir.mkdirs()
        val timestamp = dateFormat.format(Date())
        return File(dir, "${callType.name}_$timestamp${Constants.RECORDING_EXTENSION}").absolutePath
    }

    fun getRecordingDir(context: Context, callType: CallType): File {
        val base = context.getExternalFilesDir(null)
            ?: context.filesDir   // fallback to internal storage
        return File(base, "${Constants.DIR_ROOT}/${callType.dirName}")
    }

    fun fileSize(path: String): Long = File(path).length()

    fun fileName(path: String): String = File(path).name

    fun deleteFile(path: String): Boolean = File(path).let {
        if (it.exists()) it.delete() else true
    }

    /** Returns total bytes used by all recordings in all subdirs. */
    fun totalStorageUsed(context: Context): Long {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, Constants.DIR_ROOT).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024       -> "$bytes B"
        bytes < 1_048_576  -> "%.1f KB".format(bytes / 1024.0)
        else               -> "%.1f MB".format(bytes / 1_048_576.0)
    }
}
