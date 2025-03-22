package com.cap.ultimatemusicplayer

import android.app.AlertDialog
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.content.Context

class PlaylistDetailActivity : AppCompatActivity() {
    private lateinit var toolbar: Toolbar
    private lateinit var songsList: RecyclerView
    private lateinit var songsAdapter: SongsAdapter
    private lateinit var addSongsButton: FloatingActionButton
    private lateinit var emptyView: TextView
    private lateinit var playlistManager: PlaylistManager
    private var playlist: Playlist? = null
    private var playlistId: Long = -1
    
    // Service connection for music playback
    private var musicService: MusicService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            serviceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)
        
        // Setup UI components
        toolbar = findViewById(R.id.toolbar)
        songsList = findViewById(R.id.songsList)
        addSongsButton = findViewById(R.id.addSongsButton)
        emptyView = findViewById(R.id.emptyView)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Get playlist data from intent
        playlistId = intent.getLongExtra("playlist_id", -1)
        val playlistName = intent.getStringExtra("playlist_name") ?: "Unknown Playlist"
        
        if (playlistId == -1L) {
            Toast.makeText(this, "Error: Invalid playlist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Set title
        supportActionBar?.title = playlistName
        
        // Initialize playlist manager
        playlistManager = PlaylistManager(this)
        
        // Load playlist data
        loadPlaylist()
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Setup FAB
        addSongsButton.setOnClickListener {
            openAddSongsActivity()
        }
        
        // Bind to the MusicService
        bindMusicService()
    }
    
    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun unbindMusicService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun loadPlaylist() {
        playlist = playlistManager.getAllPlaylists().find { it.id == playlistId }
        
        if (playlist == null) {
            Toast.makeText(this, "Error: Playlist not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        updateEmptyView()
    }
    
    private fun setupRecyclerView() {
        songsAdapter = SongsAdapter(
            songs = playlist?.songs?.toMutableList() ?: mutableListOf(),
            onSongClick = { song ->
                // Play the song
                playSong(song)
            },
            onSongLongClick = { song ->
                // Show options menu for the song (remove from playlist, etc.)
                showSongOptionsDialog(song)
                true
            }
        )
        
        songsList.apply {
            layoutManager = LinearLayoutManager(this@PlaylistDetailActivity)
            adapter = songsAdapter
        }
    }
    
    private fun playSong(song: Song) {
        playlist?.let { currentPlaylist ->
            // Find the index of the song in the playlist
            val songIndex = currentPlaylist.songs.indexOfFirst { it.id == song.id }
            
            if (songIndex != -1) {
                // Start or use existing service
                if (serviceBound && musicService != null) {
                    // Use existing service to play song
                    musicService?.playSong(song, currentPlaylist.songs, songIndex)
                    
                    // Open SongDetailsActivity to show the now playing interface
                    val detailsIntent = Intent(this, SongDetailsActivity::class.java).apply {
                        putExtra("song_id", song.id)
                        putExtra("song_title", song.title)
                        putExtra("song_artist", song.artist)
                        putExtra("song_album", song.album)
                        putExtra("song_album_art_uri", song.albumArtUri)
                        putExtra("from_playlist", true)
                        putExtra("playlist_id", currentPlaylist.id)
                        putExtra("playlist_name", currentPlaylist.name)
                        putExtra("is_playing", true) // Indicate song should be playing
                    }
                    startActivity(detailsIntent)
                } else {
                    // Bind to the service first, then play
                    bindMusicService()
                    Handler(Looper.getMainLooper()).postDelayed({
                        playSong(song)
                    }, 500) // Try again after service binding has time to complete
                }
            }
        }
    }
    
    private fun updateEmptyView() {
        val isEmpty = playlist?.songs?.isEmpty() ?: true
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
    
    private fun showSongOptionsDialog(song: Song) {
        val options = arrayOf("Remove from playlist", "Play next", "Add to queue")
        
        AlertDialog.Builder(this)
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeSongFromPlaylist(song)
                    1 -> {
                        // Play this song next
                        if (serviceBound && musicService != null) {
                            musicService?.playNext(song)
                            Toast.makeText(this, "${song.title} will play next", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> {
                        // Add to queue
                        if (serviceBound && musicService != null) {
                            musicService?.addToQueue(song)
                            Toast.makeText(this, "${song.title} added to queue", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }
    
    private fun removeSongFromPlaylist(song: Song) {
        playlist?.let { currentPlaylist ->
            // Create a new playlist with the song removed
            val updatedSongs = currentPlaylist.songs.filter { it.id != song.id }
            val updatedPlaylist = currentPlaylist.copy(songs = updatedSongs)
            
            // Update the playlist in storage
            playlistManager.updatePlaylist(updatedPlaylist)
            
            // Update local playlist reference
            playlist = updatedPlaylist
            
            // Update UI
            songsAdapter.updateSongs(playlist?.songs?.toMutableList() ?: mutableListOf())
            updateEmptyView()
            
            Toast.makeText(this, "${song.title} removed from playlist", Toast.LENGTH_SHORT).show()
            
            // Notify MainActivity to update playlist counts
            sendPlaylistUpdateBroadcast()
        }
    }
    
    private fun openAddSongsActivity() {
        val intent = Intent(this, AddSongsToPlaylistActivity::class.java).apply {
            putExtra("playlist_id", playlistId)
            putExtra("playlist_name", playlist?.name)
        }
        startActivity(intent)
    }
    
    private fun sendPlaylistUpdateBroadcast() {
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYLIST_UPDATED").apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh playlist data when returning from AddSongsActivity
        loadPlaylist()
        songsAdapter.updateSongs(playlist?.songs?.toMutableList() ?: mutableListOf())
    }
    
    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            bindMusicService()
        }
    }
    
    override fun onStop() {
        super.onStop()
        unbindMusicService()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
