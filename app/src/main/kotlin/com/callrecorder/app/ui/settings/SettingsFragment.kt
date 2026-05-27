package com.callrecorder.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.callrecorder.app.R
import com.callrecorder.app.databinding.FragmentSettingsBinding
import com.callrecorder.app.notification.CallNotificationListener
import com.callrecorder.app.util.Constants
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.switchAutoRecord.setOnCheckedChangeListener { _, checked ->
            viewModel.setAutoRecord(checked)
        }
        binding.switchRecordVoip.setOnCheckedChangeListener { _, checked ->
            viewModel.setRecordVoip(checked)
        }
        binding.switchDarkTheme.setOnCheckedChangeListener { _, checked ->
            viewModel.setDarkTheme(checked)
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES
                else         AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        binding.switchPinLock.setOnCheckedChangeListener { _, checked ->
            viewModel.setPinEnabled(checked)
        }
        binding.switchHiddenMode.setOnCheckedChangeListener { _, checked ->
            viewModel.setHiddenMode(checked)
        }

        binding.rowQuality.setOnClickListener { showQualityDialog() }
        binding.rowAutoDelete.setOnClickListener { showAutoDeleteDialog() }

        binding.rowNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { s ->
                    binding.switchAutoRecord.isChecked  = s.autoRecord
                    binding.switchRecordVoip.isChecked  = s.recordVoip
                    binding.switchDarkTheme.isChecked   = s.darkTheme
                    binding.switchPinLock.isChecked     = s.pinEnabled
                    binding.switchHiddenMode.isChecked  = s.hiddenMode
                    binding.tvQualitySummary.text       = qualityLabel(s.quality)
                    binding.tvAutoDeleteSummary.text    = autoDeleteLabel(s.autoDeleteDays)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationAccessStatus()
    }

    private fun updateNotificationAccessStatus() {
        val granted = CallNotificationListener.isGranted(requireContext())
        binding.tvNotificationAccessStatus.text = if (granted) "Granted" else "Tap to enable"
        val color = if (granted)
            com.google.android.material.R.attr.colorPrimary
        else
            com.google.android.material.R.attr.colorError
        val resolvedColor = com.google.android.material.color.MaterialColors.getColor(
            binding.tvNotificationAccessStatus, color
        )
        binding.tvNotificationAccessStatus.setTextColor(resolvedColor)
    }

    private fun showQualityDialog() {
        val items  = arrayOf("Low (8 kHz)", "Medium (16 kHz)", "High (44 kHz)")
        val cur    = viewModel.state.value.quality
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Recording Quality")
            .setSingleChoiceItems(items, cur) { dialog, idx ->
                viewModel.setQuality(idx)
                dialog.dismiss()
            }.show()
    }

    private fun showAutoDeleteDialog() {
        val labels = Constants.AUTO_DELETE_OPTIONS.map {
            if (it == 0) "Never" else "After $it days"
        }.toTypedArray()
        val cur = Constants.AUTO_DELETE_OPTIONS.indexOf(viewModel.state.value.autoDeleteDays)
            .coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Auto-Delete Recordings")
            .setSingleChoiceItems(labels, cur) { dialog, idx ->
                viewModel.setAutoDeleteDays(Constants.AUTO_DELETE_OPTIONS[idx])
                dialog.dismiss()
            }.show()
    }

    private fun qualityLabel(q: Int) = when (q) {
        0 -> "Low"; 2 -> "High"; else -> "Medium"
    }

    private fun autoDeleteLabel(days: Int) =
        if (days == 0) "Never" else "After $days days"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
