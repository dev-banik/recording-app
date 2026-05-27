package com.callrecorder.app.ui.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.callrecorder.app.BuildConfig
import com.callrecorder.app.R
import com.callrecorder.app.databinding.FragmentPlayerBinding
import com.callrecorder.app.util.formatDate
import com.callrecorder.app.util.formatDuration
import com.callrecorder.app.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PlayerViewModel by viewModels()
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            viewModel.player.let { p ->
                if (p.isPlaying) {
                    binding.slider.value =
                        (p.currentPosition.toFloat() / p.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
                    binding.tvPosition.text = p.currentPosition.formatDuration()
                }
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupControls()
        observeState()
        progressHandler.post(progressUpdater)
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener { viewModel.playPause() }

        binding.slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val pos = (value * viewModel.player.duration).toLong()
                viewModel.seekTo(pos)
            }
        }

        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.btnRename.setOnClickListener   { showRenameDialog() }
        binding.btnDelete.setOnClickListener   { confirmDelete() }
        binding.btnShare.setOnClickListener    { shareRecording() }

        binding.chipSpeed.setOnClickListener {
            val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            val labels = speeds.map { if (it == 1.0f) "Normal" else "${it}x" }.toTypedArray()
            val current = speeds.indexOfFirst { it == viewModel.uiState.value.speed }.coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Playback Speed")
                .setSingleChoiceItems(labels, current) { dialog, idx ->
                    viewModel.setPlaybackSpeed(speeds[idx])
                    binding.chipSpeed.text = labels[idx]
                    dialog.dismiss()
                }.show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.recording?.let { rec ->
                        binding.tvTitle.text    = rec.displayName
                        binding.tvSubtitle.text =
                            "${rec.callType.label} · ${rec.timestamp.formatDate()}"
                        binding.tvDuration.text = rec.durationMs.formatDuration()
                        binding.btnFavorite.isSelected = rec.isFavorite
                    }
                    binding.btnPlayPause.setImageResource(
                        if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                    state.error?.let { requireContext().toast(it, long = true) }
                }
            }
        }
    }

    private fun showRenameDialog() {
        val input = TextInputEditText(requireContext()).apply {
            setText(viewModel.uiState.value.recording?.displayName ?: "")
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Rename Recording")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                viewModel.rename(input.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Recording")
            .setMessage("This recording will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete { findNavController().popBackStack() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareRecording() {
        val rec = viewModel.uiState.value.recording ?: return
        val file = File(rec.filePath)
        if (!file.exists()) { requireContext().toast("File not found"); return }
        val uri = FileProvider.getUriForFile(
            requireContext(), "${BuildConfig.APPLICATION_ID}.fileprovider", file
        )
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share Recording"
        ))
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressUpdater)
        super.onDestroyView()
        _binding = null
    }
}
