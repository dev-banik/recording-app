package com.callrecorder.app.recorder.strategy

import android.content.Intent
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.callrecorder.app.AppLogger
import com.callrecorder.app.RecorderApplication
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android 10+ (API 29) strategy using AudioPlaybackCapture.
 *
 * Captures the system audio output (speaker-side of a call) from other
 * apps. Combine with [MicrophoneStrategy] to record both sides.
 *
 * LIMITATIONS:
 *  - Apps that set allowAudioPlaybackCapture=false (default for most
 *    communication apps) cannot be captured this way.
 *  - Requires an active MediaProjection token granted by the user.
 *  - Best used as SUPPLEMENTARY — mix with microphone for full capture.
 *
 * To obtain the token, call MediaProjectionManager.createScreenCaptureIntent()
 * and store the result code + data before starting this strategy.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AudioPlaybackCaptureStrategy(
    private var mediaProjection: MediaProjection? = null
) : RecordingStrategy {

    override val name = "AudioPlaybackCapture(API29+)"
    override val minApiLevel = Build.VERSION_CODES.Q
    override var isRecording = false
        private set

    private var captureRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun updateProjection(projection: MediaProjection) {
        mediaProjection = projection
    }

    override fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= minApiLevel && mediaProjection != null

    override fun start(outputPath: String, quality: Int): Boolean {
        if (!isSupported()) {
            AppLogger.w(TAG, "Not supported or no MediaProjection token")
            return false
        }

        val sampleRate = 44100
        val channelMask = AudioFormat.CHANNEL_IN_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            // VOICE_COMMUNICATION is intentionally omitted — most VoIP apps
            // opt out; attempting it just returns silence.
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        val bufferSize = maxOf(minBuffer, 8192)

        captureRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()

        if (captureRecord?.state != AudioRecord.STATE_INITIALIZED) {
            AppLogger.e(TAG, "AudioRecord for playback capture failed to init")
            captureRecord?.release()
            captureRecord = null
            return false
        }

        isRecording = true
        captureJob = scope.launch {
            try {
                captureRecord!!.startRecording()
                recordToFile(captureRecord!!, outputPath, sampleRate, bufferSize)
            } finally {
                captureRecord?.stop()
                captureRecord?.release()
                captureRecord = null
                isRecording = false
                AppLogger.i(TAG, "Playback capture finished: $outputPath")
            }
        }

        AppLogger.i(TAG, "Started playback capture → $outputPath")
        return true
    }

    override fun stop() {
        isRecording = false
        captureJob?.cancel()
        captureJob = null
    }

    private suspend fun recordToFile(
        ar: AudioRecord, path: String, sampleRate: Int, bufferSize: Int
    ) = withContext(Dispatchers.IO) {
        val buf = ByteArray(bufferSize)
        val out = FileOutputStream(path)
        out.write(ByteArray(44)) // WAV header placeholder

        var total = 0L
        while (isRecording) {
            val read = ar.read(buf, 0, bufferSize)
            if (read > 0) { out.write(buf, 0, read); total += read }
        }
        out.flush(); out.close()

        // Patch WAV header
        val raf = RandomAccessFile(path, "rw")
        patchWavHeader(raf, total, sampleRate, channels = 2)
        raf.close()
    }

    private fun patchWavHeader(raf: RandomAccessFile, dataBytes: Long, sr: Int, channels: Int) {
        val byteRate = (sr * channels * 2).toLong()
        fun le32(v: Long) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
        fun le16(v: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()
        raf.seek(0)
        raf.write("RIFF".toByteArray()); raf.write(le32(dataBytes + 36))
        raf.write("WAVEfmt ".toByteArray()); raf.write(le32(16))
        raf.write(le16(1)); raf.write(le16(channels))
        raf.write(le32(sr.toLong())); raf.write(le32(byteRate))
        raf.write(le16(channels * 2)); raf.write(le16(16))
        raf.write("data".toByteArray()); raf.write(le32(dataBytes))
    }

    companion object {
        private const val TAG = "AudioPlaybackCapture"

        fun fromActivityResult(resultCode: Int, data: Intent): AudioPlaybackCaptureStrategy? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val mgr = RecorderApplication.get()
                .getSystemService(MediaProjectionManager::class.java) ?: return null
            val projection = mgr.getMediaProjection(resultCode, data) ?: return null
            return AudioPlaybackCaptureStrategy(projection)
        }
    }
}
