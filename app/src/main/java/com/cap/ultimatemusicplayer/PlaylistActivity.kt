package com.cap.ultimatemusicplayer

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.OnTouchListener
import android.view.View.OnLongClickListener
import android.app.AlertDialog
import android.text.InputType
import android.content.Intent
import android.util.Log

class PlaylistActivity : AppCompatActivity() {
    private lateinit var playlistsRecyclerView: RecyclerView
    private lateinit var playlistsAdapter: PlaylistsAdapter
    private var playlists: MutableList<Playlist> = mutableListOf()
    private lateinit var addPlaylistButton: FloatingActionButton
    private lateinit var emptyView: TextView
    private lateinit var toolbar: Toolbar
    private var isSelectionMode = false
    private lateinit var playlistManager: PlaylistManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        // Initialize PlaylistManager
        playlistManager = PlaylistManager(this)

        // Initialize views
        playlistsRecyclerView = findViewById(R.id.playlistsRecyclerView)
        addPlaylistButton = findViewById(R.id.addPlaylistButton)
        emptyView = findViewById(R.id.emptyView)
        toolbar = findViewById(R.id.toolbar)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                onBackPressed()
            }
        }

        // Setup RecyclerView
        setupRecyclerView()

        // Setup FAB click listener
        addPlaylistButton.setOnClickListener {
            showCreatePlaylistDialog()
        }

        // Load playlists
        loadPlaylists()
    }

    override fun onResume() {
        super.onResume()
        // Refresh playlists when returning to this activity
        loadPlaylists()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.playlist_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortOptions()
                true
            }
            R.id.action_search -> {
                // TODO: Implement playlist search
                Toast.makeText(this, "Search coming soon", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_select_all -> {
                if (isSelectionMode) {
                    playlistsAdapter.selectAll()
                }
                true
            }
            R.id.action_delete -> {
                if (isSelectionMode) {
                    deleteSelectedPlaylists()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupRecyclerView() {
        playlistsAdapter = PlaylistsAdapter(
            playlists = playlists,
            onPlaylistClick = { playlist ->
                if (isSelectionMode) {
                    playlistsAdapter.toggleSelection(playlist.id)
                    updateSelectionMode()
                } else {
                    showPlaylistSongs(playlist)
                }
            },
            onPlaylistLongClick = { playlist ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                    playlistsAdapter.toggleSelection(playlist.id)
                }
                true
            }
        )
        playlistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaylistActivity)
            adapter = playlistsAdapter
        }

        // Add swipe to delete functionality
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: ViewHolder,
                target: ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val playlist = playlists[position]
                    showDeleteConfirmationDialog(listOf(playlist))
                }
            }

            override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(playlistsRecyclerView)
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "Enter playlist name"
            setPadding(32, 32, 32, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val playlistName = input.text.toString().trim()
                if (playlistName.isNotEmpty()) {
                    createPlaylist(playlistName)
                } else {
                    Toast.makeText(this, "Playlist name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPlaylist(name: String) {
        try {
            val newPlaylist = Playlist(
                id = System.currentTimeMillis(),
                name = name,
                songs = emptyList()
            )
            playlistManager.addPlaylist(newPlaylist)
            loadPlaylists()
            
            // Notify MainActivity to update playlist count
            sendPlaylistUpdateBroadcast()
            
            Toast.makeText(this, "Playlist created successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating playlist: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaylistSongs(playlist: Playlist) {
        // Launch PlaylistDetailActivity to show songs in the playlist
        val intent = Intent(this, PlaylistDetailActivity::class.java).apply {
            putExtra("playlist_id", playlist.id)
            putExtra("playlist_name", playlist.name)
        }
        startActivity(intent)
    }

    private fun loadPlaylists() {
        playlists = playlistManager.getAllPlaylists().toMutableList()
        playlistsAdapter.updatePlaylists(playlists)
        updateEmptyView()
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (playlists.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSortOptions() {
        val options = arrayOf("Name (A-Z)", "Name (Z-A)", "Date Created", "Number of Songs")
        MaterialAlertDialogBuilder(this)
            .setTitle("Sort Playlists")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sortPlaylists { it.name.lowercase() }
                    1 -> sortPlaylistsDescending { it.name.lowercase() }
                    2 -> sortPlaylistsDescending { it.id }
                    3 -> sortPlaylistsDescending { it.songs.size }
                }
            }
            .show()
    }

    private fun <T : Comparable<T>> sortPlaylists(selector: (Playlist) -> T) {
        val sortedPlaylists = playlists.sortedBy(selector)
        playlists.clear()
        playlists.addAll(sortedPlaylists)
        playlistsAdapter.updatePlaylists(playlists)
    }

    private fun <T : Comparable<T>> sortPlaylistsDescending(selector: (Playlist) -> T) {
        val sortedPlaylists = playlists.sortedByDescending(selector)
        playlists.clear()
        playlists.addAll(sortedPlaylists)
        playlistsAdapter.updatePlaylists(playlists)
    }

    private fun showDeleteConfirmationDialog(playlistsToDelete: List<Playlist> = playlistsAdapter.getSelectedPlaylists()) {
        if (playlistsToDelete.isEmpty()) {
            Toast.makeText(this, "No playlists selected", Toast.LENGTH_SHORT).show()
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Playlists")
            .setMessage("Are you sure you want to delete ${playlistsToDelete.size} playlist(s)?")
            .setPositiveButton("Delete") { _, _ ->
                deletePlaylists(playlistsToDelete)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // If this was triggered by a swipe, we need to refresh the adapter
                // to restore the swiped item
                playlistsAdapter.updatePlaylists(playlists)
            }
            .show()
    }

    private fun deletePlaylists(playlistsToDelete: List<Playlist>) {
        // First delete from the PlaylistManager
        val playlistIds = playlistsToDelete.map { it.id }
        playlistManager.deletePlaylists(playlistIds)
        
        // Then update the UI
        playlists.removeAll(playlistsToDelete)
        playlistsAdapter.updatePlaylists(playlists)
        updateEmptyView()
        exitSelectionMode()
        
        // Notify MainActivity to update playlist count - use immediate notification
        sendPlaylistUpdateBroadcast()
        
        Toast.makeText(this, "${playlistsToDelete.size} playlist(s) deleted", Toast.LENGTH_SHORT).show()
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        supportActionBar?.title = "Select Playlists"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        addPlaylistButton.hide()
        invalidateOptionsMenu()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        supportActionBar?.title = "Playlists"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        addPlaylistButton.show()
        playlistsAdapter.clearSelection()
        invalidateOptionsMenu()
    }

    private fun updateSelectionMode() {
        val selectedCount = playlistsAdapter.getSelectedCount()
        supportActionBar?.title = if (selectedCount > 0) "$selectedCount selected" else "Select Playlists"
    }

    private fun deleteSelectedPlaylists() {
        val selectedPlaylists = playlistsAdapter.getSelectedPlaylists()
        if (selectedPlaylists.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Playlists")
                .setMessage("Are you sure you want to delete ${selectedPlaylists.size} playlist(s)?")
                .setPositiveButton("Delete") { _, _ ->
                    playlistManager.deletePlaylists(selectedPlaylists.map { it.id })
                    loadPlaylists()
                    exitSelectionMode()
                    
                    // Notify MainActivity to update playlist count
                    sendPlaylistUpdateBroadcast()
                    
                    Toast.makeText(this, "Playlists deleted successfully", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /**
     * Send a broadcast to notify MainActivity to update playlist counts immediately
     */
    private fun sendPlaylistUpdateBroadcast() {
        // Get the current playlist count directly
        val currentPlaylistCount = playlistManager.getAllPlaylists(true).size
        
        // Create broadcast with the count included
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYLIST_UPDATED").apply {
            // Include the count directly in the broadcast
            putExtra("playlist_count", currentPlaylistCount)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        
        // Send an ordered broadcast with higher priority
        sendOrderedBroadcast(intent, null)
        
        // Log for debugging
        Log.d("PlaylistActivity", "Sent playlist update broadcast with count: $currentPlaylistCount")
    }
}