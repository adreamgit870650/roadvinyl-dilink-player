package com.car.mp3player

import android.view.LayoutInflater
import android.view.ViewGroup
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.car.mp3player.databinding.ItemSongBinding
import com.car.mp3player.model.Song
import com.car.mp3player.model.PlaylistTextScale
import com.car.mp3player.util.TimeFormat

class SongAdapter(
    private val onClick: (Song, Int) -> Unit,
    private val onLongClick: ((Song, Int) -> Unit)? = null,
    initialTextSizeSp: Float = PlaylistTextScale.DEFAULT
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    private var textSizeSp = PlaylistTextScale.clamp(initialTextSizeSp)

    var playingPath: String? = null
        set(value) {
            if (field == value) return
            val old = field
            field = value
            currentList.forEachIndexed { index, song ->
                if (song.path == old || song.path == value) {
                    notifyItemChanged(index, PAYLOAD_PLAYING)
                }
            }
        }

    fun submitSongs(list: List<Song>, commitCallback: (() -> Unit)? = null) {
        submitList(list) { commitCallback?.invoke() }
    }

    fun setPlaylistTextSize(value: Float) {
        val normalized = PlaylistTextScale.clamp(value)
        if (normalized == textSizeSp) return
        textSizeSp = normalized
        if (itemCount > 0) notifyItemRangeChanged(0, itemCount, PAYLOAD_TEXT_SIZE)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) return super.onBindViewHolder(holder, position, payloads)
        val song = getItem(position)
        if (payloads.contains(PAYLOAD_PLAYING)) holder.bindPlayingState(song)
        if (payloads.contains(PAYLOAD_TEXT_SIZE)) holder.applyTextSizes()
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(song: Song, position: Int) {
            applyTextSizes()
            binding.songIndex.text = "${position + 1}."
            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist
            binding.songDuration.text = TimeFormat.mmss(song.durationMs)
            binding.lrcBadge.visibility = if (song.lrcPath != null) android.view.View.VISIBLE else android.view.View.GONE
            bindPlayingState(song)
            binding.root.setOnClickListener { onClick(song, position) }
            binding.root.setOnLongClickListener {
                onLongClick?.invoke(song, position)
                onLongClick != null
            }
        }

        fun applyTextSizes() {
            val sizes = PlaylistTextScale.sizes(textSizeSp)
            binding.songIndex.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.secondarySp)
            binding.songTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.primarySp)
            binding.songArtist.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.secondarySp)
            binding.songDuration.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.secondarySp)
            binding.lrcBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.badgeSp)
            binding.playingBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizes.badgeSp)
        }

        fun bindPlayingState(song: Song) {
            val isPlaying = song.path == playingPath
            binding.playingBadge.visibility = if (isPlaying) android.view.View.VISIBLE else android.view.View.GONE
            binding.root.setBackgroundColor(
                if (isPlaying) ContextCompat.getColor(binding.root.context, R.color.playing_highlight)
                else android.graphics.Color.TRANSPARENT
            )
        }
    }

    companion object {
        private const val PAYLOAD_PLAYING = "playing"
        private const val PAYLOAD_TEXT_SIZE = "text_size"
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.path == newItem.path
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
