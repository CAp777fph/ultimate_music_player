package com.cap.ultimatemusicplayer

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class AddSongsToPlaylistActivity : AppCompatActivity() {
    private lateinit var toolbar: Toolbar
    private lateinit var songsList: RecyclerView
    private lateinit var songsAdapter: SongsAdapter
    private lateinit var playlistManager: PlaylistManager
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playCountManager: PlayCountManager
    private var songs: MutableList<Song> = mutableListOf()
    private var playlist: Playlist? = null
    private var playlistId: Long = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_songs_to_playlist)
        
        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        songsList = findViewById(R.id.songsList)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Get playlist ID from intent
        playlistId = intent.getLongExtra("playlist_id", -1)
        val playlistName = intent.getStringExtra("playlist_name") ?: "Unknown Playlist"
        
        if (playlistId == -1L) {
            Toast.makeText(this, "Error: Invalid playlist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Set title
        supportActionBar?.title = "Add Songs to $playlistName"
        
        // Initialize playlist manager
        playlistManager = PlaylistManager(this)
        favoritesManager = FavoritesManager(this)
        playCountManager = PlayCountManager(this)
        
        // Get playlist by ID
        playlist = playlistManager.getAllPlaylists().find { it.id == playlistId }
        
        if (playlist == null) {
            Toast.makeText(this, "Error: Playlist not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Load all songs from the device
        loadSongs()
        
        // Setup RecyclerView
        setupRecyclerView()
    }
    
    private fun loadSongs() {
        val contentResolver = contentResolver
        val uri = Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            Media._ID,
            Media.TITLE,
            Media.ARTIST,
            Media.ALBUM,
            Media.DURATION,
            Media.DATA,
            Media.ALBUM_ID
        )
        
        // Define a selection that excludes very short audio files
        val selection = "${Media.IS_MUSIC} != 0 AND ${Media.DURATION} > 30000"
        val sortOrder = "${Media.TITLE} ASC"
        
        val tempSongs = mutableListOf<Song>()
        
        contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(Media.ALBUM_ID)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown Title"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(dataColumn)
                val albumId = cursor.getLong(albumIdColumn)
                
                val albumArtUri = "content://media/external/audio/albumart/$albumId"
                
                val song = Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    path = path,
                    albumArtUri = albumArtUri,
                    isFavorite = favoritesManager.isFavorite(id),
                    playCount = playCountManager.getPlayCount(id)
                )
                
                tempSongs.add(song)
            }
        }
        
        // Filter out songs that are already in the playlist
        val playlistSongIds = playlist?.songs?.map { it.id } ?: emptyList()
        this.songs = tempSongs.filter { it.id !in playlistSongIds }.toMutableList()
        
        // Update empty view visibility
        findViewById<TextView>(R.id.emptyView).visibility = if (this.songs.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun setupRecyclerView() {
        songsAdapter = SongsAdapter(
            songs = songs,
            onSongClick = { song ->
                addSongToPlaylist(song)
            },
            onSongLongClick = { song ->
                // No long press action needed for this activity
                false
            }
        )
        
        songsList.apply {
            layoutManager = LinearLayoutManager(this@AddSongsToPlaylistActivity)
            adapter = songsAdapter
        }
    }
    
    private fun addSongToPlaylist(song: Song) {
        // Add song to playlist
        playlistManager.addSongToPlaylist(playlistId, song)
        
        // Show confirmation to user
        Toast.makeText(
            this,
            "${song.title} added to ${playlist?.name}",
            Toast.LENGTH_SHORT
        ).show()
        
        // Remove the song from the current list
        songs.remove(song)
        songsAdapter.updateSongs(songs)
        
        // Notify MainActivity to update playlist count
        sendPlaylistUpdateBroadcast()
    }
    
    private fun sendPlaylistUpdateBroadcast() {
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYLIST_UPDATED").apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
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
