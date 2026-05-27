package com.callrecorder.app.recorder.strategy

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.callrecorder.app.AppLogger
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records via AudioRecord — lower-level than MediaRecorder, sometimes bypasses
 * ROM-level restrictions on mic access during calls.
 *
 * [audioSource] defaults to MIC but can be VOICE_RECOGNITION (bypasses call
 * recording blocks on some ROMs), UNPROCESSED (raw ADC), etc.
 *
 * Writes a WAV file so no encoder dependency is needed.
 */
class MicrophoneStrategy(
    private val audioSource: Int = MediaRecorder.AudioSource.MIC,
    private val forceSampleRate: Int? = null,
) : RecordingStrategy {

    override val name = "AudioRecord(source=$audioSource,sr=${forceSampleRate ?: "auto"})"
    override val minApiLevel = 26
    override var isRecording = false
        private set

    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun isSupported(): Boolean = true

    override fun start(outputPath: String, quality: Int): Boolean {
        val sampleRate = forceSampleRate ?: if (quality >= 2) 44100 else 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, encoding)
        if (minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            AppLogger.e(TAG, "Cannot create AudioRecord — bad config")
            return false
        }
        val bufferSize = maxOf(minBuffer, 4096)

        val audioRecord = AudioRecord(
            audioSource,
            sampleRate, channelConfig, encoding, bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            AppLogger.e(TAG, "AudioRecord failed to initialise")
            audioRecord.release()
            return false
        }

        isRecording = true
        recordJob = scope.launch {
            try {
                audioRecord.startRecording()
                writeWav(audioRecord, outputPath, sampleRate, bufferSize)
            } finally {
                audioRecord.stop()
                audioRecord.release()
                isRecording = false
                AppLogger.i(TAG, "MIC recording finished: $outputPath")
            }
        }

        AppLogger.i(TAG, "Started mic recording → $outputPath")
        return true
    }

    override fun stop() {
        isRecording = false
        // Don't cancel — let the coroutine finish the WAV header write naturally.
        // The while(isRecording) loop exits on the next read() cycle (~100ms).
        recordJob = null
    }

    private suspend fun writeWav(
        audioRecord: AudioRecord,
        path: String,
        sampleRate: Int,
        bufferSize: Int
    ) = withContext(Dispatchers.IO) {
        val pcmBuffer = ByteArray(bufferSize)
        val out = FileOutputStream(path)

        // Write placeholder WAV header; fill sizes at the end
        out.write(ByteArray(44))

        var totalBytes = 0L
        while (isRecording) {
            val read = audioRecord.read(pcmBuffer, 0, bufferSize)
            if (read > 0) {
                out.write(pcmBuffer, 0, read)
                totalBytes += read
            }
        }
        out.flush()
        out.close()

        // Patch WAV header with real sizes
        val raf = RandomAccessFile(path, "rw")
        writeWavHeader(raf, totalBytes, sampleRate)
        raf.close()
    }

    private fun writeWavHeader(raf: RandomAccessFile, dataBytes: Long, sampleRate: Int) {
        val byteRate   = sampleRate * 2L      // mono 16-bit
        val totalSize  = dataBytes + 36

        fun le32(v: Long): ByteArray = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()
        fun le16(v: Int): ByteArray = ByteBuffer.allocate(2)
            .order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()

        raf.seek(0)
        raf.write("RIFF".toByteArray())
        raf.write(le32(totalSize))
        raf.write("WAVEfmt ".toByteArray())
        raf.write(le32(16))       // PCM chunk size
        raf.write(le16(1))        // PCM format
        raf.write(le16(1))        // mono
        raf.write(le32(sampleRate.toLong()))
        raf.write(le32(byteRate))
        raf.write(le16(2))        // block align (mono 16-bit)
        raf.write(le16(16))       // bits per sample
        raf.write("data".toByteArray())
        raf.write(le32(dataBytes))
    }

    companion object {
        private const val TAG = "MicrophoneStrategy"
    }
}
