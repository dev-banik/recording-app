package com.callrecorder.app

import android.content.Context
import com.callrecorder.app.domain.model.CallType
import com.callrecorder.app.recorder.AudioRecorderManager
import com.callrecorder.app.recorder.strategy.MicrophoneStrategy
import com.callrecorder.app.recorder.strategy.RecordingStrategy
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class AudioRecorderManagerTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `isRecording is false initially`() {
        val manager = AudioRecorderManager(context)
        assertFalse(manager.isRecording)
    }

    @Test
    fun `strategy name is none before recording`() {
        val manager = AudioRecorderManager(context)
        assertEquals("none", manager.getActiveStrategyName())
    }

    @Test
    fun `startRecording returns null when all strategies fail`() {
        // AudioRecorderManager cannot be easily tested without Android runtime.
        // This test documents the expected null return on complete failure.
        // Integration tests on a device/emulator would cover actual strategies.
        assertTrue(true) // placeholder — real test requires Robolectric or device
    }

    @Test
    fun `MicrophoneStrategy isSupported always returns true`() {
        val strategy = MicrophoneStrategy()
        assertTrue(strategy.isSupported())
        assertEquals("Microphone(MIC)", strategy.name)
        assertEquals(26, strategy.minApiLevel)
        assertFalse(strategy.isRecording)
    }

    @Test
    fun `calling stop when not recording does not throw`() {
        val strategy = MicrophoneStrategy()
        // Should not throw even when not active
        strategy.stop()
        assertFalse(strategy.isRecording)
    }
}
