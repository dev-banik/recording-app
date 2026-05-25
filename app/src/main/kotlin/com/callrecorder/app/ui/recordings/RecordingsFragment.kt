package com.callrecorder.app.ui.recordings

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.callrecorder.app.R
import com.callrecorder.app.databinding.FragmentRecordingsBinding
import com.callrecorder.app.domain.model.RecordingDomain
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecordingsFragment : Fragment() {

    private var _binding: FragmentRecordingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordingsViewModel by viewModels()
    private lateinit var adapter: RecordingAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordingsBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupChipFilters()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = RecordingAdapter(
            onPlay     = ::openPlayer,
            onFavorite = viewModel::toggleFavorite,
            onDelete   = ::confirmDelete,
        )
        binding.rvRecordings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RecordingsFragment.adapter
        }
    }

    private fun setupChipFilters() {
        binding.chipAll.setOnClickListener       { viewModel.setFilter(null) }
        binding.chipPhone.setOnClickListener     { viewModel.setFilter("PHONE") }
        binding.chipWhatsapp.setOnClickListener  { viewModel.setFilter("WHATSAPP") }
        binding.chipTelegram.setOnClickListener  { viewModel.setFilter("TELEGRAM") }
        binding.chipMessenger.setOnClickListener { viewModel.setFilter("MESSENGER") }
        binding.chipVoip.setOnClickListener      { viewModel.setFilter("VOIP") }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.recordings)
                    binding.tvEmpty.isVisible = state.recordings.isEmpty() && !state.isLoading
                    binding.progressBar.isVisible = state.isLoading
                }
            }
        }
    }

    private fun openPlayer(recording: RecordingDomain) {
        val action = RecordingsFragmentDirections.actionRecordingsToPlayer(recording.id)
        findNavController().navigate(action)
    }

    private fun confirmDelete(recording: RecordingDomain) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Recording")
            .setMessage("Delete \"${recording.displayName}\"? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.delete(recording) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_recordings, menu)
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as? SearchView)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true.also { viewModel.setQuery(query ?: "") }
            override fun onQueryTextChange(query: String?) = true.also { viewModel.setQuery(query ?: "") }
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val order = when (item.itemId) {
            R.id.sort_date_desc     -> SortOrder.DATE_DESC
            R.id.sort_date_asc      -> SortOrder.DATE_ASC
            R.id.sort_duration_desc -> SortOrder.DURATION_DESC
            R.id.sort_name_asc      -> SortOrder.NAME_ASC
            else                    -> return super.onOptionsItemSelected(item)
        }
        viewModel.setSortOrder(order)
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
