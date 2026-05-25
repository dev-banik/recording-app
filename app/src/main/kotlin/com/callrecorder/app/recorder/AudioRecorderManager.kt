package com.callrecorder.app.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import com.callrecorder.app.AppLogger
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.recorder.strategy.*
import com.callrecorder.app.util.Constants
import com.callrecorder.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates recording strategies in priority order.
 *
 * Phone calls:
 *   1. VOICE_CALL source       (best — both sides; most manufacturer ROMs)
 *   2. VOICE_DOWNLINK source   (incoming audio only)
 *   3. VOICE_UPLINK source     (outgoing audio only)
 *   4. MIC fallback            (your voice + speaker bleed)
 *
 * VoIP calls:
 *   1. AudioPlaybackCapture    (Android 10+; speaker-side)
 *   2. VOICE_COMMUNICATION     (mic + some ROMs capture both)
 *   3. MIC fallback
 */
@Singleton
class AudioRecorderManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var activeStrategy: RecordingStrategy? = null
    private var currentFilePath: String? = null
    private var recordingStartMs: Long = 0L

    val isRecording: Boolean get() = activeStrategy?.isRecording == true
    val elapsedMs: Long get() = if (isRecording) System.currentTimeMillis() - recordingStartMs else 0L

    // Holds the AudioPlaybackCapture strategy so the projection token survives
    private var playbackCaptureStrategy: AudioPlaybackCaptureStrategy? = null

    fun setPlaybackCaptureStrategy(s: AudioPlaybackCaptureStrategy) {
        playbackCaptureStrategy = s
    }

    /**
     * Start recording for the given call type and quality.
     * @return The output file path on success, null on failure.
     */
    fun startRecording(callType: CallType, quality: Int): String? {
        if (isRecording) {
            AppLogger.w(TAG, "Already recording — ignoring startRecording()")
            return null
        }

        val outputPath = FileUtils.createOutputFile(context, callType)
        val strategies = buildStrategyList(callType)

        for (strategy in strategies) {
            if (!strategy.isSupported()) continue
            AppLogger.d(TAG, "Trying strategy: ${strategy.name}")
            if (strategy.start(outputPath, quality)) {
                activeStrategy = strategy
                currentFilePath = outputPath
                recordingStartMs = System.currentTimeMillis()
                AppLogger.i(TAG, "Recording started with ${strategy.name} → $outputPath")
                return outputPath
            } else {
                AppLogger.w(TAG, "Strategy ${strategy.name} failed, trying next…")
            }
        }

        AppLogger.e(TAG, "All strategies failed for callType=$callType")
        return null
    }

    /**
     * Stop the active recording.
     * @return Duration in milliseconds.
     */
    fun stopRecording(): Long {
        val duration = elapsedMs
        activeStrategy?.stop()
        activeStrategy = null
        currentFilePath = null
        AppLogger.i(TAG, "Recording stopped after ${duration}ms")
        return duration
    }

    fun getActivePath(): String? = currentFilePath

    fun getActiveStrategyName(): String = activeStrategy?.name ?: "none"

    // ── Strategy priority lists ────────────────────────────────────────────

    private fun buildStrategyList(callType: CallType): List<RecordingStrategy> =
        when (callType) {
            CallType.PHONE -> phoneStrategies()
            else           -> voipStrategies()
        }

    private fun phoneStrategies(): List<RecordingStrategy> = buildList {
        add(MediaRecorderStrategy.voiceCall())
        add(MediaRecorderStrategy.voiceDownlink())
        add(MediaRecorderStrategy.voiceUplink())
        add(MediaRecorderStrategy.voiceCommunication())
        add(MediaRecorderStrategy.voiceRecognition())
        add(MediaRecorderStrategy.unprocessed())
        add(MicrophoneStrategy())
    }

    private fun voipStrategies(): List<RecordingStrategy> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            playbackCaptureStrategy?.let { add(it) }
        }
        // MediaRecorder-based (higher-level, MIUI may restrict these during VoIP)
        add(MediaRecorderStrategy.voiceCall())         // both sides on many ROMs
        add(MediaRecorderStrategy.voiceRecognition())  // mic, avoids communication path
        add(MediaRecorderStrategy.unprocessed())       // raw ADC mic

        // AudioRecord-based (lower-level, sometimes bypasses ROM call-recording blocks)
        add(MicrophoneStrategy(MediaRecorder.AudioSource.VOICE_RECOGNITION))
        add(MicrophoneStrategy(MediaRecorder.AudioSource.UNPROCESSED))
        add(MicrophoneStrategy())  // raw MIC via AudioRecord, last resort

        // NOTE: voiceCommunication() excluded — shares WhatsApp's audio path, causes mutual silence.
    }

    companion object {
        private const val TAG = "AudioRecorderManager"
    }
}
