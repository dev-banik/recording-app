package com.callrecorder.app.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.fragment.app.Fragment
import com.callrecorder.app.databinding.FragmentPermissionsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionsFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshPermissionViews() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        refreshPermissionViews()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionViews()
    }

    private fun setupButtons() {
        binding.btnGrantBasic.setOnClickListener {
            permLauncher.launch(basicPermissions())
        }
        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOpenAppSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }
        binding.btnOpenOverlay.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            })
        }
    }

    private fun refreshPermissionViews() {
        val ctx = requireContext()
        fun check(p: String) = ContextCompat.checkSelfPermission(ctx, p) ==
            PermissionChecker.PERMISSION_GRANTED

        binding.rowRecordAudio.setGranted(check(Manifest.permission.RECORD_AUDIO))
        binding.rowPhoneState.setGranted(check(Manifest.permission.READ_PHONE_STATE))
        binding.rowCallLog.setGranted(check(Manifest.permission.READ_CALL_LOG))
        binding.rowContacts.setGranted(check(Manifest.permission.READ_CONTACTS))
        binding.rowNotifications.setGranted(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                check(Manifest.permission.POST_NOTIFICATIONS)
            else true
        )
        binding.rowAccessibility.setGranted(isAccessibilityEnabled())
        binding.rowOverlay.setGranted(Settings.canDrawOverlays(ctx))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${requireContext().packageName}/" +
            "com.callrecorder.app.accessibility.CallMonitorAccessibilityService"
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabled.contains(service)
    }

    private fun basicPermissions() = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Extension on the permission row view — update icon/text based on grant status
private fun View.setGranted(granted: Boolean) {
    alpha = if (granted) 1.0f else 0.5f
}
