package com.callrecorder.app.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.domain.repository.RecordingRepository
import com.callrecorder.app.domain.usecase.DeleteRecordingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val recording: RecordingDomain? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val error: String? = null,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: RecordingRepository,
    private val deleteUseCase: DeleteRecordingUseCase,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val recordingId: Long = checkNotNull(savedState["recordingId"])

    private val _state = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context).build().also { p ->
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                _state.update { it.copy(durationMs = p.duration.coerceAtLeast(0)) }
            }
        })
    }

    init {
        loadRecording()
    }

    private fun loadRecording() {
        viewModelScope.launch {
            val rec = repository.getById(recordingId)
            if (rec == null) {
                _state.update { it.copy(error = "Recording not found") }
                return@launch
            }
            _state.update { it.copy(recording = rec, durationMs = rec.durationMs) }
            val file = File(rec.filePath)
            if (file.exists()) {
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                player.prepare()
            } else {
                _state.update { it.copy(error = "File not found: ${rec.filePath}") }
            }
        }
    }

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val rec = _state.value.recording ?: return@launch
            repository.setFavorite(rec.id, !rec.isFavorite)
            _state.update { it.copy(recording = rec.copy(isFavorite = !rec.isFavorite)) }
        }
    }

    fun rename(newLabel: String) {
        viewModelScope.launch {
            val id = recordingId
            repository.setLabel(id, newLabel)
            _state.update { it.copy(recording = it.recording?.copy(customLabel = newLabel)) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val rec = _state.value.recording ?: return@launch
            player.stop()
            deleteUseCase(rec.id, rec.filePath)
            onDeleted()
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
