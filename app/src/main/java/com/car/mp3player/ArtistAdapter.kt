package com.car.mp3player

import android.view.LayoutInflater
import android.view.ViewGroup
import android.util.TypedValue
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.car.mp3player.databinding.ItemArtistBinding
import com.car.mp3player.model.ArtistGroup
import com.car.mp3player.model.PlaylistTextScale

class ArtistAdapter(
    private val onClick: (ArtistGroup) -> Unit,
    initialTextSizeSp: Float = PlaylistTextScale.DEFAULT
) : ListAdapter<ArtistGroup, ArtistAdapter.ArtistViewHolder>(DIFF) {

    private var textSizeSp = PlaylistTextScale.clamp(initialTextSizeSp)

    fun setPlaylistTextSize(value: Float) {
        val normalized = PlaylistTextScale.clamp(value)
        if (normalized == textSizeSp) return
        textSizeSp = normalized
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_TEXT_SIZE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val binding = ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ArtistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TEXT_SIZE)) holder.applyTextSizes()
        else super.onBindViewHolder(holder, position, payloads)
    }

    inner class ArtistViewHolder(private val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: ArtistGroup) {
            applyTextSizes()
            binding.artistName.text = group.name
            binding.artistCount.text = binding.root.context.getString(R.string.artist_song_count, group.songCount)
            binding.root.setOnClickListener { onClick(group) }
        }

        fun applyTextSizes() {
            val sizes = PlaylistTextScale.sizes(textSizeSp)
            binding.artistName.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.primarySp)
            binding.artistCount.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.secondarySp)
        }
    }

    companion object {
        private const val PAYLOAD_TEXT_SIZE = "text_size"
        private val DIFF = object : DiffUtil.ItemCallback<ArtistGroup>() {
            override fun areItemsTheSame(oldItem: ArtistGroup, newItem: ArtistGroup) =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: ArtistGroup, newItem: ArtistGroup) =
                oldItem == newItem
        }
    }
}
