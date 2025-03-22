package com.cap.ultimatemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PlaylistsAdapter(
    private var playlists: List<Playlist>,
    private val onPlaylistClick: (Playlist) -> Unit,
    private val onPlaylistLongClick: (Playlist) -> Boolean
) : RecyclerView.Adapter<PlaylistsAdapter.PlaylistViewHolder>() {

    private val selectedPlaylists = mutableSetOf<Long>()

    class PlaylistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playlistName: TextView = view.findViewById(R.id.playlistName)
        val songCount: TextView = view.findViewById(R.id.songCount)
        val checkBox: CheckBox = view.findViewById(R.id.checkBox)
        val root: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.playlistName.text = playlist.name
        holder.songCount.text = "${playlist.songs.size} songs"
        
        // Handle selection state
        holder.checkBox.visibility = if (selectedPlaylists.isNotEmpty()) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedPlaylists.contains(playlist.id)
        
        // Update background based on selection
        holder.root.setBackgroundColor(
            if (selectedPlaylists.contains(playlist.id)) {
                ContextCompat.getColor(holder.itemView.context, R.color.selection_color)
            } else {
                ContextCompat.getColor(holder.itemView.context, R.color.card_background)
            }
        )
        
        holder.itemView.setOnClickListener {
            onPlaylistClick(playlist)
        }
        
        holder.itemView.setOnLongClickListener {
            onPlaylistLongClick(playlist)
        }
    }

    override fun getItemCount() = playlists.size

    fun updatePlaylists(newPlaylists: List<Playlist>) {
        playlists = newPlaylists
        notifyDataSetChanged()
    }

    fun toggleSelection(playlistId: Long) {
        if (selectedPlaylists.contains(playlistId)) {
            selectedPlaylists.remove(playlistId)
        } else {
            selectedPlaylists.add(playlistId)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedPlaylists.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedPlaylists.clear()
        selectedPlaylists.addAll(playlists.map { it.id })
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedPlaylists.size

    fun getSelectedPlaylists(): List<Playlist> {
        return playlists.filter { selectedPlaylists.contains(it.id) }
    }
} 