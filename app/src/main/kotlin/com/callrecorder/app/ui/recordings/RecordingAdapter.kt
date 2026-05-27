package com.callrecorder.app.ui.recordings

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callrecorder.app.R
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
            binding.tvName.text = item.displayName
            binding.tvMeta.text = "${item.timestamp.formatDate()} · ${item.durationMs.formatDuration()}"
            binding.tvType.text = item.callType.label
            binding.tvSize.text = FileUtils.formatFileSize(item.fileSizeBytes)

            // Switch icon and tint based on favourite state
            if (item.isFavorite) {
                binding.btnFavorite.setIconResource(R.drawable.ic_star)
                binding.btnFavorite.iconTint = ColorStateList.valueOf(Color.parseColor("#FFD600"))
            } else {
                binding.btnFavorite.setIconResource(R.drawable.ic_star_outline)
                binding.btnFavorite.iconTint = null  // restore default theme tint
            }

            binding.root.setOnClickListener        { onPlay(item) }
            binding.btnFavorite.setOnClickListener { onFavorite(item) }
            binding.btnDelete.setOnClickListener   { onDelete(item) }
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
