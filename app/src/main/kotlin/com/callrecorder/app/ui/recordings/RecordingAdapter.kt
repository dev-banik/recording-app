package com.callrecorder.app.ui.recordings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.app.databinding.ItemRecordingBinding
import com.callrecorder.app.domain.model.RecordingDomain
import com.callrecorder.app.util.FileUtils
import com.callrecorder.app.util.formatDate
import com.callrecorder.app.util.formatDuration

class RecordingAdapter(
    private val onPlay:     (RecordingDomain) -> Unit,
    private val onFavorite: (RecordingDomain) -> Unit,
    private val onDelete:   (RecordingDomain) -> Unit,
) : ListAdapter<RecordingDomain, RecordingAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemRecordingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RecordingDomain) {
            binding.tvName.text       = item.displayName
            binding.tvMeta.text       = "${item.timestamp.formatDate()} · ${item.durationMs.formatDuration()}"
            binding.tvType.text       = item.callType.label
            binding.tvSize.text       = FileUtils.formatFileSize(item.fileSizeBytes)
            binding.btnFavorite.isSelected = item.isFavorite

            binding.root.setOnClickListener         { onPlay(item) }
            binding.btnFavorite.setOnClickListener  { onFavorite(item) }
            binding.btnDelete.setOnClickListener    { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemRecordingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RecordingDomain>() {
            override fun areItemsTheSame(a: RecordingDomain, b: RecordingDomain) = a.id == b.id
            override fun areContentsTheSame(a: RecordingDomain, b: RecordingDomain) = a == b
        }
    }
}
