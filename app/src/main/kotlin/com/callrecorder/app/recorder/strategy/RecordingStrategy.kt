package com.callrecorder.app.recorder.strategy

/**
 * Strategy interface for all recording back-ends.
 *
 * Each implementation encapsulates one recording approach with its own
 * limitations (API level, permissions, manufacturer quirks). The manager
 * tries strategies in priority order and falls back on failure.
 */
interface RecordingStrategy {

    /** Human-readable name logged to identify which strategy was used. */
    val name: String

    /** Minimum Android API level this strategy supports. */
    val minApiLevel: Int

    /**
     * Returns true when the strategy is likely to succeed on this device.
     * Implementations may check API level, permissions, or root state.
     */
    fun isSupported(): Boolean

    /**
     * Start recording. Returns true on success.
     * @param outputPath  Absolute path where the audio file will be written.
     * @param quality     0 = low, 1 = medium, 2 = high.
     */
    fun start(outputPath: String, quality: Int = 1): Boolean

    /** Stop recording and finalise the output file. */
    fun stop()

    /** True while recording is actively in progress. */
    val isRecording: Boolean
}
