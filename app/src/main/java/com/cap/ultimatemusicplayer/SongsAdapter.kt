package com.cap.ultimatemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.core.content.ContextCompat
import com.cap.ultimatemusicplayer.databinding.ItemSongBinding
import com.google.android.material.card.MaterialCardView

class SongsAdapter(
    private var songs: List<Song>,
    private val onSongClick: (Song) -> Unit,
    private val onSongLongClick: (Song) -> Boolean
) : RecyclerView.Adapter<SongsAdapter.SongViewHolder>() {

    private var selectedSongs = mutableSetOf<Long>()
    private var isSelectionMode = false

    inner class SongViewHolder(private val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song) {
            binding.apply {
                songTitle.text = song.title
                artistName.text = song.artist
                duration.text = formatDuration(song.duration)

                // Load album art using Glide
                Glide.with(albumArt)
                    .load(song.albumArtUri)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .into(albumArt)

                // Handle selection state
                checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                checkBox.isChecked = selectedSongs.contains(song.id)

                // Update card background based on selection state
                (root as MaterialCardView).apply {
                    strokeWidth = if (selectedSongs.contains(song.id)) 2 else 0
                    strokeColor = if (selectedSongs.contains(song.id)) 
                        ContextCompat.getColor(context, R.color.accent_color) 
                    else 
                        0
                    setCardBackgroundColor(
                        if (selectedSongs.contains(song.id))
                            ContextCompat.getColor(context, R.color.selected_item_background)
                        else
                            ContextCompat.getColor(context, R.color.card_background)
                    )
                }

                // Handle click events
                root.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(song.id)
                    } else {
                        onSongClick(song)
                    }
                }

                root.setOnLongClickListener {
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        toggleSelection(song.id)
                    }
                    onSongLongClick(song)
                }

                // Handle checkbox clicks
                checkBox.setOnClickListener {
                    toggleSelection(song.id)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(songs[position])
    }

    override fun getItemCount(): Int = songs.size

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs
        clearSelection()
    }

    fun toggleSelection(songId: Long) {
        if (selectedSongs.contains(songId)) {
            selectedSongs.remove(songId)
            if (selectedSongs.isEmpty()) {
                isSelectionMode = false
            }
        } else {
            selectedSongs.add(songId)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedSongs.clear()
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedSongs.size

    fun getSelectedSongs(): Set<Long> = selectedSongs.toSet()

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedSongs.clear()
        }
        notifyDataSetChanged()
    }

    fun isInSelectionMode(): Boolean = isSelectionMode

    private fun formatDuration(duration: Long): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60
        return String.format("%d:%02d", minutes, seconds)
    }
} 