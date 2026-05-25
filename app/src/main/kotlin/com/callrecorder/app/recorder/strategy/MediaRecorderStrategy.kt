package com.callrecorder.app.recorder.strategy

import android.media.MediaRecorder
import android.os.Build
import com.callrecorder.app.AppLogger

/**
 * Uses [MediaRecorder] with VOICE_CALL audio source.
 *
 * VOICE_CALL captures both sides of a GSM / CDMA phone call on most
 * manufacturer ROMs (Samsung, Xiaomi, OPPO, Vivo, Realme, OnePlus).
 * On stock Android 9+ Google restricts this source — the strategy
 * will fail silently and the manager falls back to [MicrophoneStrategy].
 *
 * Fallback sources attempted in order:
 *   1. VOICE_CALL
 *   2. VOICE_DOWNLINK  (incoming audio only)
 *   3. VOICE_UPLINK    (outgoing audio only)
 *   4. VOICE_COMMUNICATION
 */
class MediaRecorderStrategy(
    private val audioSource: Int = MediaRecorder.AudioSource.VOICE_CALL
) : RecordingStrategy {

    override val name = "MediaRecorder(source=$audioSource)"
    override val minApiLevel = 26
    override var isRecording = false
        private set

    private var recorder: MediaRecorder? = null

    override fun isSupported(): Boolean = Build.VERSION.SDK_INT >= minApiLevel

    override fun start(outputPath: String, quality: Int): Boolean {
        return try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(com.callrecorder.app.RecorderApplication.get())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            val (sampleRate, bitRate) = qualityParams(quality)

            recorder!!.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(sampleRate)
                setAudioEncodingBitRate(bitRate)
                setAudioChannels(1)
                setOutputFile(outputPath)
                prepare()
                start()
            }

            isRecording = true
            AppLogger.i(TAG, "Started: source=$audioSource path=$outputPath")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start: ${e.message}", e)
            safeRelease()
            false
        }
    }

    override fun stop() {
        try {
            recorder?.apply {
                stop()
                reset()
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error stopping: ${e.message}")
        } finally {
            safeRelease()
            isRecording = false
        }
    }

    private fun safeRelease() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    private fun qualityParams(quality: Int): Pair<Int, Int> = when (quality) {
        0    -> 8_000  to 32_000
        2    -> 44_100 to 128_000
        else -> 16_000 to 64_000
    }

    companion object {
        private const val TAG = "MediaRecorderStrategy"

        /** Convenience factories for each fallback source. */
        fun voiceCall()          = MediaRecorderStrategy(MediaRecorder.AudioSource.VOICE_CALL)
        fun voiceDownlink()      = MediaRecorderStrategy(MediaRecorder.AudioSource.VOICE_DOWNLINK)
        fun voiceUplink()        = MediaRecorderStrategy(MediaRecorder.AudioSource.VOICE_UPLINK)
        fun voiceCommunication() = MediaRecorderStrategy(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    }
}
