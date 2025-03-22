package com.cap.ultimatemusicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.File

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var playPauseButton: FloatingActionButton
    private lateinit var previousButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var shuffleButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var searchBar: EditText
    private lateinit var backButton: ImageButton
    private lateinit var topNavigation: TabLayout
    private lateinit var favoriteSongCard: MaterialCardView
    private lateinit var favoriteSongCount: TextView
    private lateinit var newSongsCard: MaterialCardView
    private lateinit var mostPlayedCard: MaterialCardView
    private lateinit var seekBar: SeekBar
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var albumArt: ImageView
    private lateinit var songsList: RecyclerView
    private lateinit var songsAdapter: SongsAdapter
    private lateinit var miniPlayerInfo: LinearLayout
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var playCountManager: PlayCountManager
    private lateinit var equalizerManager: EqualizerManager
    private lateinit var playlistManager: PlaylistManager
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentSongPosition = -1
    private var songs = mutableListOf<Song>()
    private var folders = mutableListOf<Folder>()
    var isShuffleEnabled = false
        private set
    var repeatMode = RepeatMode.NONE
        private set
    private var shuffledIndices = mutableListOf<Int>()
    private var currentShuffleIndex = -1
    private var isMuted = false
    private var actionMode: ActionMode? = null
    private lateinit var bottomPlaybackControls: LinearLayout
    private lateinit var unplayedSongCount: TextView
    private lateinit var mostPlayedCount: TextView
    private var musicService: MusicService? = null
    private var serviceBound = false
    private lateinit var albumsList: RecyclerView
    private lateinit var albumsAdapter: AlbumsAdapter
    private var albums = mutableListOf<Album>()
    private lateinit var viewPager: ViewPager2
    private var artists = mutableListOf<Artist>()
    private lateinit var songsFragment: SongsFragment
    private lateinit var albumsFragment: AlbumsFragment
    private lateinit var artistsFragment: ArtistsFragment
    private lateinit var foldersFragment: FoldersFragment
    private var currentAlbumSongs: MutableList<Song>? = null
    private var isViewingAlbumSongs = false
    private var isSelectionMode = false
    private lateinit var currentView: ViewType
    private lateinit var playlistCount: TextView

    // Add this as a class property
    private val playlistUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.PLAYLIST_UPDATED") {
                updatePlaylistCount()
            }
        }
    }

    enum class ViewType {
        SONGS,
        ALBUMS,
        ARTISTS,
        FOLDERS
    }

    private val playbackControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.PLAYBACK_CONTROL") {
                when (intent.getStringExtra("command")) {
                    "TOGGLE_PLAY_PAUSE" -> togglePlayPause()
                    "PLAY_NEXT" -> playNext()
                    "PLAY_PREVIOUS" -> playPrevious()
                    "TOGGLE_SHUFFLE" -> {
                        isShuffleEnabled = !isShuffleEnabled
                        if (isShuffleEnabled) {
                            shuffleButton.setImageResource(R.drawable.ic_shuffle)
                            shuffleButton.setColorFilter(ContextCompat.getColor(context!!, R.color.accent_color))
                            generateShuffleIndices()
                            Toast.makeText(context, "শাফল চালু করা হয়েছে", Toast.LENGTH_SHORT).show()
                        } else {
                            shuffleButton.setImageResource(R.drawable.ic_shuffle)
                            shuffleButton.clearColorFilter()
                            shuffledIndices.clear()
                            currentShuffleIndex = -1
                            Toast.makeText(context, "শাফল বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                        updatePlaybackButtonStates()
                    }
                    "CHANGE_REPEAT_MODE" -> {
                        repeatMode = when (repeatMode) {
                            RepeatMode.NONE -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.NONE
                        }
                        musicService?.setRepeatMode(repeatMode)
                        updateRepeatButton()
                    }
                    "SET_SPEED" -> {
                        val speed = intent.getFloatExtra("speed", 1.0f)
                        Log.d("SpeedControl", "MainActivity received SET_SPEED command: $speed")
                        musicService?.setPlaybackSpeed(speed)
                    }
                    "MUTE" -> {
                        musicService?.setVolume(0f, 0f)
                        isMuted = true
                        sendPlaybackStateUpdate()
                    }
                    "UNMUTE" -> {
                        musicService?.setVolume(1f, 1f)
                        isMuted = false
                        sendPlaybackStateUpdate()
                    }
                    "GET_CURRENT_POSITION" -> {
                        musicService?.let { service ->
                            val intent = Intent("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE").apply {
                                setPackage(packageName)
                                putExtra("current_position", service.playerPosition)
                                putExtra("duration", service.duration)
                            }
                            sendBroadcast(intent)
                        }
                    }
                    "SEEK_TO" -> {
                        val position = intent.getIntExtra("position", 0)
                        musicService?.seekTo(position)
                    }
                    "SHOW_PLAYLIST" -> {
                        // Implement playlist view
                        Toast.makeText(context, "Playlist view coming soon", Toast.LENGTH_SHORT).show()
                    }
                    "EDIT_SONG" -> {
                        // Implement song editing
                        Toast.makeText(context, "Song editing coming soon", Toast.LENGTH_SHORT).show()
                    }
                    "ADD_TO_FAVORITE" -> {
                        val songId = intent.getLongExtra("song_id", -1L)
                        if (songId != -1L) {
                            val songIndex = songs.indexOfFirst { it.id == songId }
                            if (songIndex != -1) {
                                songs[songIndex].isFavorite = true
                                favoritesManager.addFavorite(songId)
                                updateFavoriteCount()
                                Toast.makeText(context, "প্রিয় তালিকায় যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "REMOVE_FROM_FAVORITE" -> {
                        val songId = intent.getLongExtra("song_id", -1L)
                        if (songId != -1L) {
                            val songIndex = songs.indexOfFirst { it.id == songId }
                            if (songIndex != -1) {
                                songs[songIndex].isFavorite = false
                                favoritesManager.removeFavorite(songId)
                                updateFavoriteCount()
                                Toast.makeText(context, "প্রিয় তালিকা থেকে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    "SHOW_EQUALIZER" -> {
                        // Implement equalizer
                        Toast.makeText(context, "Equalizer coming soon", Toast.LENGTH_SHORT).show()
                    }
                    "SHOW_TIMER" -> {
                        // Implement sleep timer
                        Toast.makeText(context, "Sleep timer coming soon", Toast.LENGTH_SHORT).show()
                    }
                    "SHOW_VOLUME" -> {
                        // Show system volume dialog
                        val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                    }
                    "GET_SONG_LIST" -> {
                        // Send song list to SongDetailsActivity
                        val intent = Intent("com.cap.ultimatemusicplayer.SONG_LIST_UPDATE").apply {
                            setPackage(packageName)
                            putParcelableArrayListExtra("songs", ArrayList(songs))
                        }
                        sendBroadcast(intent, null)
                        Toast.makeText(context, "Sending ${songs.size} songs", Toast.LENGTH_SHORT).show()
                    }
                    "PLAY_SONG" -> {
                        val songId = intent.getLongExtra("song_id", -1)
                        if (songId != -1L) {
                            val songIndex = songs.indexOfFirst { it.id == songId }
                            if (songIndex != -1) {
                                currentSongPosition = songIndex
                                playSong(songIndex)
                            }
                        }
                    }
                    "GET_FAVORITE_STATUS" -> {
                        val songId = intent.getLongExtra("song_id", -1L)
                        if (songId != -1L) {
                            val isFavorite = favoritesManager.isFavorite(songId)
                            val responseIntent = Intent("com.cap.ultimatemusicplayer.FAVORITE_STATUS_UPDATE").apply {
                                setPackage(packageName)
                                putExtra("song_id", songId)
                                putExtra("is_favorite", isFavorite)
                            }
                            sendBroadcast(responseIntent)
                        }
                    }
                }

                // Send updated state back to SongDetailsActivity
                sendBroadcast(Intent("com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE").apply {
                    setPackage(packageName)
                    putExtra("is_playing", isPlaying)
                    putExtra("is_shuffle_enabled", isShuffleEnabled)
                    putExtra("repeat_mode", repeatMode.ordinal)
                    putExtra("is_muted", isMuted)
                })
            }
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE" -> {
                    val isPlaying = intent.getBooleanExtra("is_playing", false)
                    updatePlaybackUI(isPlaying)
                    updateBottomControlsVisibility(isPlaying)
                }
            }
        }
    }

    enum class RepeatMode {
        NONE,       // No repeat
        ALL,        // Repeat all songs
        ONE        // Repeat current song
    }

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            loadSongs()
        } else {
            showPermissionExplanationDialog()
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.songs_selection_menu, menu)
            isSelectionMode = true
            songsAdapter.setSelectionMode(true)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val selectedCount = songsAdapter.getSelectedCount()
            mode.title = if (selectedCount == 1) "1 song selected" else "$selectedCount songs selected"
            menu.findItem(R.id.action_rename)?.isVisible = selectedCount == 1
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    val selectedSongs = songsAdapter.getSelectedSongs()
                    if (selectedSongs.isNotEmpty()) {
                    showDeleteConfirmationDialog()
                    }
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog()
                    true
                }
                R.id.action_share -> {
                    shareSongs()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            isSelectionMode = false
            songsAdapter.setSelectionMode(false)
            songsAdapter.clearSelection()
            notifySelectionModeChanged()
        }
    }

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

    private fun convertToBengaliNumerals(number: Int): String {
        return number.toString().map { digit ->
            when (digit) {
                '0' -> '০'
                '1' -> '১'
                '2' -> '২'
                '3' -> '৩'
                '4' -> '৪'
                '5' -> '৫'
                '6' -> '৬'
                '7' -> '৭'
                '8' -> '৮'
                '9' -> '৯'
                else -> digit
            }
        }.joinToString("")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        favoritesManager = FavoritesManager(this)
        playCountManager = PlayCountManager(this)
        equalizerManager = EqualizerManager(this)
        playlistManager = PlaylistManager(this)

        // Register broadcast receivers with explicit export flag
        val playbackControlFilter = IntentFilter("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL")
        registerReceiver(playbackControlReceiver, playbackControlFilter, Context.RECEIVER_NOT_EXPORTED)

        val stateFilter = IntentFilter("com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE")
        registerReceiver(stateReceiver, stateFilter, Context.RECEIVER_NOT_EXPORTED)

        // Register playlist update receiver
        val playlistUpdateFilter = IntentFilter("com.cap.ultimatemusicplayer.PLAYLIST_UPDATED")
        registerReceiver(playlistUpdateReceiver, playlistUpdateFilter)

        // Bind to MusicService
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            startService(intent)
        }

        initializeViews()
        setupClickListeners()
        setupSearchBar()
        setupTopNavigation()
        setupSeekBar()
        checkPermissions()
        
        // Update favorite count on start
        updateFavoriteCount()

        // Initialize the bottom playback controls
        bottomPlaybackControls = findViewById(R.id.bottomPlaybackControls)
        
        // Initially hide the controls
        bottomPlaybackControls.visibility = View.GONE

        // Initialize ViewPager2
        viewPager = findViewById(R.id.viewPager)
        setupViewPager()

        // Initialize artists after songs are loaded
        artists = getArtists()
        
        // Update ArtistsFragment if it exists
        (supportFragmentManager.fragments.find { it is ArtistsFragment } as? ArtistsFragment)?.updateArtists(artists)
    }

    private fun initializeViews() {
        playPauseButton = findViewById(R.id.playPauseButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        repeatButton = findViewById(R.id.repeatButton)
        searchBar = findViewById(R.id.searchBar)
        backButton = findViewById(R.id.backButton)
        topNavigation = findViewById(R.id.topNavigation)
        favoriteSongCard = findViewById(R.id.favoriteSongCard)
        favoriteSongCount = findViewById(R.id.favoriteSongCount)
        newSongsCard = findViewById(R.id.newSongsCard)
        mostPlayedCard = findViewById(R.id.mostPlayedCard)
        seekBar = findViewById(R.id.seekBar)
        songTitle = findViewById(R.id.songTitle)
        artistName = findViewById(R.id.artistName)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        albumArt = findViewById(R.id.albumArt)
        songsList = findViewById(R.id.songsList)
        miniPlayerInfo = findViewById(R.id.miniPlayerInfo)
        unplayedSongCount = findViewById(R.id.unplayedSongCount)
        mostPlayedCount = findViewById(R.id.mostPlayedCount)
        albumsList = findViewById(R.id.albumsList)
        playlistCount = findViewById(R.id.playlistCount)

        // Initialize all songs count
        findViewById<TextView>(R.id.allSongCount)?.text = songs.size.toString()

        setupRecyclerViews()
        updateFavoriteCount()
        updatePlaylistCount()
        setupBackButton()
    }

    private fun setupRecyclerViews() {
        // Setup songs RecyclerView
        songsAdapter = SongsAdapter(
            songs = songs,
            onSongClick = { song ->
                if (actionMode != null) {
                    songsAdapter.toggleSelection(song.id)
                    actionMode?.invalidate()
                } else {
                    val songPosition = songs.indexOf(song)
                    playSong(songPosition)
                    
                    val intent = Intent(this, SongDetailsActivity::class.java).apply {
                        putExtra("song_id", song.id)
                        putExtra("song_title", song.title)
                        putExtra("song_artist", song.artist)
                        putExtra("album_art_uri", song.albumArtUri)
                        putExtra("is_playing", true)
                        putExtra("is_shuffle_enabled", isShuffleEnabled)
                        putExtra("repeat_mode", repeatMode.ordinal)
                        putExtra("is_favorite", song.isFavorite)
                    }
                    startActivity(intent)
                }
            },
            onSongLongClick = { song ->
                if (actionMode == null) {
                    actionMode = startSupportActionMode(actionModeCallback)
                    songsAdapter.toggleSelection(song.id)
                    actionMode?.invalidate()
                }
                true
            }
        )
        songsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songsAdapter
            setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimensionPixelSize(R.dimen.bottom_playback_height))
            clipToPadding = false
        }

        // Setup albums RecyclerView
        albumsAdapter = AlbumsAdapter(
            albums = albums,
            onAlbumClick = { album ->
                // Show songs from this album
                val albumSongs = songs.filter { it.album == album.name }
                songsAdapter.updateSongs(albumSongs)
                topNavigation.getTabAt(0)?.select() // Switch to Songs tab
                Toast.makeText(this, "${album.name} এর গানগুলি দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
            }
        )
        albumsList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = albumsAdapter
            setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimensionPixelSize(R.dimen.bottom_playback_height))
            clipToPadding = false
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            // Verify we have both READ and WRITE permissions
            val hasReadPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            val hasWritePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13 and above, we don't need explicit WRITE permission
                true
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            if (hasReadPermission && hasWritePermission) {
            loadSongs()
            } else {
                permissionLauncher.launch(requiredPermissions)
            }
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun showPermissionExplanationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("পারমিশন প্রয়োজন")
            .setMessage("মিউজিক প্লেয়ার ব্যবহার করার জন্য আপনার ডিভাইসের স্টোরেজ এবং নোটিফিকেশন পারমিশন প্রয়োজন। দয়া করে পারমিশন দিন।")
            .setPositiveButton("পারমিশন দিন") { _, _ ->
                permissionLauncher.launch(requiredPermissions)
            }
            .setNegativeButton("বাতিল করুন") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(
                    this,
                    "পারমিশন ছাড়া অ্যাপ ব্যবহার করা যাবে না",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun loadSongs() {
        val projection = arrayOf(
            Media._ID,
            Media.TITLE,
            Media.ARTIST,
            Media.ALBUM,
            Media.DURATION,
            Media.DATA,
            Media.ALBUM_ID
        )

        val selection = "${Media.IS_MUSIC} != 0"
        val sortOrder = "${Media.TITLE} ASC"

        contentResolver.query(
            Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(Media.DATA)
            val albumIdColumn = cursor.getColumnIndexOrThrow(Media.ALBUM_ID)

            songs.clear()
            val audioFolders = mutableMapOf<String, MutableList<Song>>()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(pathColumn)
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

                songs.add(song)

                // Group songs by folder
                val folderPath = File(path).parent ?: continue
                val folderSongs = audioFolders.getOrPut(folderPath) { mutableListOf() }
                folderSongs.add(song)
            }

            // Create Folder objects
            folders.clear()
            audioFolders.forEach { (path, songs) ->
                val folder = File(path)
                folders.add(
                    Folder(
                        name = folder.name,
                        path = path,
                        numberOfSongs = songs.size,
                        songs = songs
                    )
                )
            }

            // Sort folders by name
            folders.sortBy { it.name }

            songsAdapter.updateSongs(songs)
            updateFavoriteCount()
            updateUnplayedCount()
            updateMostPlayedCount()
            
            // Update all songs count
            findViewById<TextView>(R.id.allSongCount)?.text = songs.size.toString()
            
            currentView = ViewType.SONGS
        }
        
        // After loading songs, organize them into albums
        organizeAlbums()

        // After loading songs and organizing albums, update fragments
        updateFragments()

        // After songs are loaded, update artists
        artists = getArtists()
        (supportFragmentManager.fragments.find { it is ArtistsFragment } as? ArtistsFragment)?.updateArtists(artists)
    }

    private fun updateFragments() {
        // Update SongsFragment
        (supportFragmentManager.fragments.find { it is SongsFragment } as? SongsFragment)?.updateSongs(songs)
        
        // Update AlbumsFragment
        (supportFragmentManager.fragments.find { it is AlbumsFragment } as? AlbumsFragment)?.updateAlbums(albums)

        // Update FoldersFragment
        (supportFragmentManager.fragments.find { it is FoldersFragment } as? FoldersFragment)?.updateFolders(folders)
    }

    private fun organizeAlbums() {
        val albumMap = mutableMapOf<String, MutableList<Song>>()
        
        // Group songs by album
        songs.forEach { song ->
            val albumSongs = albumMap.getOrPut(song.album) { mutableListOf() }
            albumSongs.add(song)
        }
        
        // Create Album objects
        albums.clear()
        albumMap.forEach { (albumName, albumSongs) ->
            val firstSong = albumSongs.first()
            albums.add(
                Album(
                    id = firstSong.id,
                    name = albumName,
                    artist = if (albumSongs.distinctBy { it.artist }.size == 1) {
                        firstSong.artist
                    } else {
                        "Various Artists"
                    },
                    albumArtUri = firstSong.albumArtUri ?: "content://media/external/audio/albumart/${firstSong.id}",
                    numberOfSongs = albumSongs.size,
                    songs = albumSongs
                )
            )
        }
        
        // Sort albums by name
        albums.sortBy { it.name }
        
        // Update the adapter
        albumsAdapter.updateAlbums(albums)

        // After organizing albums, update fragments
        updateFragments()
    }

    private fun updatePlaybackButtonStates() {
        val songsToPlay = currentAlbumSongs ?: songs
        if (songsToPlay.isEmpty()) {
            // If no songs, disable all buttons
            nextButton.isEnabled = false
            previousButton.isEnabled = false
            nextButton.alpha = 0.5f
            previousButton.alpha = 0.5f
            return
        }

        // Handle next button state
        val hasNextSong = when {
            repeatMode == RepeatMode.ONE -> true // Always enabled if repeat one is on
            isShuffleEnabled -> shuffledIndices.size > 1
            else -> currentSongPosition < songsToPlay.size - 1 || repeatMode == RepeatMode.ALL
        }
        nextButton.isEnabled = hasNextSong
        nextButton.alpha = if (hasNextSong) 1.0f else 0.5f

        // Handle previous button state
        val hasPreviousSong = when {
            repeatMode == RepeatMode.ONE -> true // Always enabled if repeat one is on
            isShuffleEnabled -> shuffledIndices.size > 1
            else -> currentSongPosition > 0 || repeatMode == RepeatMode.ALL
        }
        previousButton.isEnabled = hasPreviousSong
        previousButton.alpha = if (hasPreviousSong) 1.0f else 0.5f
    }

    fun playNext() {
        val songsToPlay = currentAlbumSongs ?: songs
        if (songsToPlay.isEmpty()) return
        
        val newPosition = when {
            repeatMode == RepeatMode.ONE -> {
                currentSongPosition // Stay on current song
            }
            isShuffleEnabled -> {
                currentShuffleIndex = (currentShuffleIndex + 1) % shuffledIndices.size
                shuffledIndices[currentShuffleIndex]
            }
            else -> {
                val nextPos = (currentSongPosition + 1) % songsToPlay.size
                if (nextPos == 0 && repeatMode != RepeatMode.ALL) {
                    stopPlayback()
                    return
                }
                nextPos
            }
        }
        
        // Play the song and update UI
        playSong(newPosition)
        
        // Send update to SongDetailsActivity
        val currentSong = songsToPlay[newPosition]
        sendBroadcast(Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
            setPackage(packageName)
            putExtra("song_id", currentSong.id)
            putExtra("song_title", currentSong.title)
            putExtra("song_artist", currentSong.artist)
            putExtra("album_art_uri", currentSong.albumArtUri)
            putExtra("is_favorite", currentSong.isFavorite)
        })
    }

    fun playPrevious() {
        val songsToPlay = currentAlbumSongs ?: songs
        if (songsToPlay.isEmpty()) return
        
        val newPosition = when {
            repeatMode == RepeatMode.ONE -> {
                currentSongPosition // Stay on current song
            }
            isShuffleEnabled -> {
                currentShuffleIndex = if (currentShuffleIndex > 0) {
                    currentShuffleIndex - 1
                } else {
                    shuffledIndices.size - 1
                }
                shuffledIndices[currentShuffleIndex]
            }
            else -> {
                if (currentSongPosition > 0) {
                    currentSongPosition - 1
                } else if (repeatMode == RepeatMode.ALL) {
                    songsToPlay.size - 1
                } else {
                    stopPlayback()
                    return
                }
            }
        }
        
        // Play the song and update UI
        playSong(newPosition)
        
        // Send update to SongDetailsActivity
        val currentSong = songsToPlay[newPosition]
        sendBroadcast(Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
            setPackage(packageName)
            putExtra("song_id", currentSong.id)
            putExtra("song_title", currentSong.title)
            putExtra("song_artist", currentSong.artist)
            putExtra("album_art_uri", currentSong.albumArtUri)
            putExtra("is_favorite", currentSong.isFavorite)
        })
    }

    fun playSong(position: Int) {
        val songsToPlay = currentAlbumSongs ?: songs
        if (songsToPlay.isEmpty()) {
            Toast.makeText(this, "No songs available", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            currentSongPosition = position
            val song = songsToPlay[position]

            // Enhanced debugging for file paths
            Log.d("MainActivity", "Attempting to play song: ${song.title}")
            Log.d("MainActivity", "Song path: ${song.path}")
            
            // Verify the song file exists and is readable
            val file = File(song.path)
            Log.d("MainActivity", "File exists: ${file.exists()}")
            Log.d("MainActivity", "File can read: ${file.canRead()}")
            Log.d("MainActivity", "File length: ${if (file.exists()) file.length() else "N/A"}")
            
            if (!file.exists()) {
                Toast.makeText(this, "Song file not found", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Song file does not exist: ${song.path}")
                return
            }
            
            if (!file.canRead()) {
                Toast.makeText(this, "Song file not readable", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Song file is not readable: ${song.path}")
                return
            }

            // Use MusicService for playback
            try {
                // Create a new Song object with a verified path
                val verifiedSong = Song(
                    id = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    duration = song.duration,
                    path = file.absolutePath, // Use absolute path to ensure consistency
                    albumArtUri = song.albumArtUri,
                    isFavorite = song.isFavorite,
                    playCount = playCountManager.getPlayCount(song.id)
                )
                
                Log.d("MainActivity", "Passing verified song to MusicService with path: ${verifiedSong.path}")
                musicService?.playSong(verifiedSong)
            isPlaying = true

            // Update UI elements
            songTitle.text = song.title
            artistName.text = song.artist
            playPauseButton.setImageResource(R.drawable.ic_pause)

            // Update shuffle index if needed
            if (isShuffleEnabled) {
                currentShuffleIndex = shuffledIndices.indexOf(position)
            }

                // Update play count
                playCountManager.incrementPlayCount(song.id)
                songs[position].playCount = playCountManager.getPlayCount(song.id)
            updateUnplayedCount()
            updateMostPlayedCount()

            // Show bottom controls
            updateBottomControlsVisibility(true)

                // Update playback button states
                updatePlaybackButtonStates()

                // Notify SongDetailsActivity about the song change
                sendBroadcast(Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
                    putExtra("song_id", song.id)
                    putExtra("song_title", song.title)
                    putExtra("song_artist", song.artist)
                    putExtra("album_art_uri", song.albumArtUri)
                })

        } catch (e: Exception) {
                Log.e("MainActivity", "Error in MusicService.playSong: ${e.message}", e)
                Toast.makeText(this, "Error playing song: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("MainActivity", "Error in playSong: ${e.message}", e)
            Toast.makeText(this, "গান চালাতে সমস্যা হচ্ছে", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun setupClickListeners() {
        playPauseButton.setOnClickListener { togglePlayPause() }
        previousButton.setOnClickListener { playPrevious() }
        nextButton.setOnClickListener { playNext() }
        shuffleButton.setOnClickListener { toggleShuffle() }
        repeatButton.setOnClickListener { toggleRepeat() }
        
        // Add All Songs card click listener
        findViewById<MaterialCardView>(R.id.allSongCard)?.setOnClickListener {
            // Switch to Songs tab
            viewPager.currentItem = 0
            
            // Show all songs
            songsAdapter.updateSongs(songs)
            songsFragment.updateSongs(songs)
            
            // Reset view state
            isViewingAlbumSongs = false
            currentAlbumSongs = null
            backButton.visibility = View.GONE
            
            // Update song count in the card
            findViewById<TextView>(R.id.allSongCount)?.text = songs.size.toString()
            
            // Show toast message in Bengali
            Toast.makeText(this, "সব গান দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
        }
        
        favoriteSongCard.setOnClickListener { 
            // Switch to Songs tab
            viewPager.currentItem = 0
            
            // Filter favorite songs
            val favoriteSongs = songs.filter { favoritesManager.isFavorite(it.id) }
            
            // Update both the adapter and fragment
            songsAdapter.updateSongs(favoriteSongs)
            songsFragment.updateSongs(favoriteSongs)
            
            // Show toast message in Bengali
            Toast.makeText(this, "প্রিয় গানের তালিকা দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
        }
        
        newSongsCard.setOnClickListener {
            // Switch to Songs tab
            viewPager.currentItem = 0
            
            // Filter unplayed songs
            val unplayedSongs = songs.filter { it.playCount == 0 }
            
            // Update both the adapter and fragment
            songsAdapter.updateSongs(unplayedSongs)
            songsFragment.updateSongs(unplayedSongs)
            
            // Show toast message in Bengali
            Toast.makeText(this, "অচলিত গানগুলি দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
        }
        
        mostPlayedCard.setOnClickListener {
            // Switch to Songs tab
            viewPager.currentItem = 0
            
            // Get songs with play count > 0 and sort by play count in descending order
            val mostPlayedSongs = songs.filter { it.playCount > 0 }
                .sortedByDescending { it.playCount }
                .take(20) // Show top 20 most played songs

            if (mostPlayedSongs.isNotEmpty()) {
                // Update both the adapter and fragment
                songsAdapter.updateSongs(mostPlayedSongs)
                songsFragment.updateSongs(mostPlayedSongs)
                
                // Show toast message in Bengali
                Toast.makeText(this, "সর্বাধিক চালানো গানগুলি দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
            } else {
                // Show message if no songs have been played yet
                Toast.makeText(this, "এখনও কোন গান চালানো হয়নি", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up completion listener for auto-play
        mediaPlayer?.setOnCompletionListener {
            playNextBasedOnMode()
        }

        miniPlayerInfo.setOnClickListener {
            if (currentSongPosition != -1 && songs.isNotEmpty()) {
                val currentSong = songs[currentSongPosition]
                val intent = Intent(this, SongDetailsActivity::class.java).apply {
                    putExtra("song_id", currentSong.id)
                    putExtra("song_title", currentSong.title)
                    putExtra("song_artist", currentSong.artist)
                    putExtra("album_art_uri", currentSong.albumArtUri)
                    putExtra("is_playing", mediaPlayer?.isPlaying ?: false)
                    putExtra("is_shuffle_enabled", isShuffleEnabled)
                    putExtra("repeat_mode", repeatMode.ordinal)
                    putExtra("is_favorite", currentSong.isFavorite)
                }
                startActivity(intent)
            }
        }

        // Add playlist button click listener
        findViewById<CardView>(R.id.playList)?.setOnClickListener {
            val intent = Intent(this, PlaylistActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupSearchBar() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                filterSongs(query)
            }
            override fun afterTextChanged(s: Editable?) {
                // Ensure the list is restored when the search field is cleared
                if (s?.isEmpty() == true) {
                    filterSongs("")
                }
            }
        })
    }

    private fun setupTopNavigation() {
        topNavigation.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showSongs()
                    1 -> showAlbums()
                    2 -> showArtists()
                    3 -> showFolders()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun filterSongs(query: String) {
        // Get the current fragment based on ViewPager position
        val currentFragment = when (viewPager.currentItem) {
            0 -> supportFragmentManager.fragments.find { it is SongsFragment } as? SongsFragment
            1 -> supportFragmentManager.fragments.find { it is AlbumsFragment } as? AlbumsFragment
            2 -> supportFragmentManager.fragments.find { it is ArtistsFragment } as? ArtistsFragment
            3 -> supportFragmentManager.fragments.find { it is FoldersFragment } as? FoldersFragment
            else -> null
        }

        // Filter based on current view
        when (currentFragment) {
            is SongsFragment -> {
                if (query.isEmpty()) {
                    // Restore original songs list based on context
                    if (isViewingAlbumSongs) {
                        currentFragment.updateSongs(currentAlbumSongs ?: songs)
        } else {
                        currentFragment.updateSongs(songs)
                    }
                } else {
                    val songsToFilter = if (isViewingAlbumSongs) {
                        currentAlbumSongs ?: songs
                    } else {
                        songs
                    }
                    val filteredSongs = songsToFilter.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
                    }
                    currentFragment.updateSongs(filteredSongs)
                }
            }
            is AlbumsFragment -> {
                if (query.isEmpty()) {
                    // Restore original albums list
                    currentFragment.updateAlbums(albums)
                } else {
                    val filteredAlbums = albums.filter { album ->
                        album.name.contains(query, ignoreCase = true) ||
                        album.artist.contains(query, ignoreCase = true)
                    }
                    currentFragment.updateAlbums(filteredAlbums)
                }
            }
            is ArtistsFragment -> {
                if (query.isEmpty()) {
                    // Restore original artists list
                    currentFragment.updateArtists(artists)
                } else {
                    val filteredArtists = artists.filter { artist ->
                        artist.name.contains(query, ignoreCase = true)
                    }
                    currentFragment.updateArtists(filteredArtists)
                }
            }
            is FoldersFragment -> {
                if (query.isEmpty()) {
                    // Restore original folders list
                    currentFragment.updateFolders(folders)
                } else {
                    val filteredFolders = folders.filter { folder ->
                        folder.name.contains(query, ignoreCase = true)
                    }
                    currentFragment.updateFolders(filteredFolders)
                }
            }
        }
    }

    private fun showFavoriteSongs() {
        val favoriteSongs = songs.filter { favoritesManager.isFavorite(it.id) }
        songsAdapter.updateSongs(favoriteSongs)
        Toast.makeText(this, "প্রিয় গানের তালিকা দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
    }

    private fun showSongs() {
        viewPager.currentItem = 0
    }

    private fun showAlbums() {
        viewPager.currentItem = 1
    }

    private fun showArtists() {
        viewPager.currentItem = 2
    }

    private fun showFolders() {
        viewPager.currentItem = 3
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicService?.seekTo(progress)
                    currentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Register for seekbar updates
        val seekBarFilter = IntentFilter("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE")
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val currentPosition = intent?.getIntExtra("current_position", 0) ?: 0
                val duration = intent?.getIntExtra("duration", 0) ?: 0
                seekBar.max = duration
                seekBar.progress = currentPosition
                currentTime.text = formatTime(currentPosition)
                totalTime.text = formatTime(duration)
            }
        }, seekBarFilter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun togglePlayPause() {
        if (currentSongPosition == -1 && songs.isNotEmpty()) {
            playSong(0)
            return
        }

        musicService?.togglePlayPause()
        isPlaying = musicService?.isPlaying == true
        updatePlaybackUI(isPlaying)
    }

    private fun updateSeekBar() {
        musicService?.let { musicService ->
            seekBar.progress = musicService.playerPosition
            currentTime.text = formatTime(musicService.playerPosition)
        }
    }

    private fun updateCurrentTime() {
        musicService?.let { service ->
            currentTime.text = formatTime(service.playerPosition)
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun sendSeekBarUpdate() {
        musicService?.let { service ->
            val intent = Intent("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE").apply {
                setPackage(packageName)
                putExtra("current_position", service.playerPosition)
                putExtra("duration", service.duration)
            }
            sendBroadcast(intent)
        }
    }

    private fun playNextBasedOnMode() {
        when {
            repeatMode == RepeatMode.ONE -> {
                // Repeat the same song
                playSong(currentSongPosition)
                
                // Send update to SongDetailsActivity
                val currentSong = songs[currentSongPosition]
                sendBroadcast(Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("song_id", currentSong.id)
                    putExtra("song_title", currentSong.title)
                    putExtra("song_artist", currentSong.artist)
                    putExtra("album_art_uri", currentSong.albumArtUri)
                    putExtra("is_favorite", currentSong.isFavorite)
                })
            }
            isShuffleEnabled -> {
                // Play next shuffled song
                currentShuffleIndex = (currentShuffleIndex + 1) % shuffledIndices.size
                val newPosition = shuffledIndices[currentShuffleIndex]
                playSong(newPosition)
                
                // Send update to SongDetailsActivity
                val currentSong = songs[newPosition]
                sendBroadcast(Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
                    setPackage(packageName)
                    putExtra("song_id", currentSong.id)
                    putExtra("song_title", currentSong.title)
                    putExtra("song_artist", currentSong.artist)
                    putExtra("album_art_uri", currentSong.albumArtUri)
                    putExtra("is_favorite", currentSong.isFavorite)
                })
            }
            else -> {
                // Normal sequential play
                val nextPosition = (currentSongPosition + 1) % songs.size
                if (nextPosition == 0 && repeatMode != RepeatMode.ALL) {
                    // Stop playing if we've reached the end and repeat all is not enabled
                    stopPlayback()
                } else {
                    playSong(nextPosition)
                    
                    // Send update to SongDetailsActivity
                    val currentSong = songs[nextPosition]
                    sendBroadcast(Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
                        setPackage(packageName)
                        putExtra("song_id", currentSong.id)
                        putExtra("song_title", currentSong.title)
                        putExtra("song_artist", currentSong.artist)
                        putExtra("album_art_uri", currentSong.albumArtUri)
                        putExtra("is_favorite", currentSong.isFavorite)
                    })
                }
            }
        }
    }

    private fun stopPlayback() {
        musicService?.let {
        isPlaying = false
        updatePlaybackUI(false)
            handler.removeCallbacks(::updateSeekBar)
        }
        actionMode?.finish()
    }

    private fun showSortOptions() {
        val options = arrayOf("Title (A-Z)", "Title (Z-A)", "Artist (A-Z)", "Artist (Z-A)", "Album", "Date Added")
        MaterialAlertDialogBuilder(this)
            .setTitle("Sort Songs")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> sortSongs { it.title.lowercase() }
                    1 -> sortSongsDescending { it.title.lowercase() }
                    2 -> sortSongs { it.artist.lowercase() }
                    3 -> sortSongsDescending { it.artist.lowercase() }
                    4 -> sortSongs { it.album.lowercase() }
                    5 -> sortSongs { it.id }
                }
            }
            .show()
    }

    private fun <T : Comparable<T>> sortSongs(selector: (Song) -> T) {
        val sortedSongs = songs.sortedBy(selector)
        songs.clear()
        songs.addAll(sortedSongs)
        songsAdapter.updateSongs(songs)
    }

    private fun <T : Comparable<T>> sortSongsDescending(selector: (Song) -> T) {
        val sortedSongs = songs.sortedByDescending(selector)
        songs.clear()
        songs.addAll(sortedSongs)
        songsAdapter.updateSongs(songs)
    }

    private fun sendPlaybackStateUpdate() {
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE").apply {
            putExtra("is_playing", mediaPlayer?.isPlaying ?: false)
            putExtra("is_shuffle_enabled", isShuffleEnabled)
            putExtra("repeat_mode", repeatMode)
            putExtra("is_muted", isMuted)
            }
            sendBroadcast(intent)
    }

    private fun sendPlaybackStateUpdate(isMuted: Boolean) {
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE").apply {
            putExtra("is_playing", mediaPlayer?.isPlaying ?: false)
            putExtra("is_shuffle_enabled", isShuffleEnabled)
            putExtra("repeat_mode", repeatMode)
            putExtra("is_muted", isMuted)
        }
        sendBroadcast(intent)
    }

    private fun updateFavoriteCount() {
        val count = favoritesManager.getAllFavorites().size
        favoriteSongCount.text = count.toString()
        // Update the favorite card visibility based on count
        favoriteSongCard.alpha = if (count > 0) 1.0f else 0.5f
    }

    private fun showDeleteConfirmationDialog() {
        val selectedSongIds = songsAdapter.getSelectedSongs()
        if (selectedSongIds.isEmpty()) {
            Toast.makeText(this, "No songs selected", Toast.LENGTH_SHORT).show()
            return
        }

        val message = if (selectedSongIds.size == 1) {
            "Are you sure you want to delete this song?"
        } else {
            "Are you sure you want to delete ${selectedSongIds.size} songs?"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Songs")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
                deleteSongs(selectedSongs)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSongs(songs: List<Song>) {
        try {
            var successCount = 0
            var failureCount = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+ (API 30+), use MediaStore.createDeleteRequest
                val urisToDelete = songs.mapNotNull { song ->
                    try {
                        ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, song.id)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error creating URI for song ${song.title}: ${e.message}")
                        null
                    }
                }

                if (urisToDelete.isNotEmpty()) {
                    Log.d("MainActivity", "Using MediaStore.createDeleteRequest for ${urisToDelete.size} songs")
                    
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, urisToDelete)
                        startIntentSenderForResult(
                            pendingIntent.intentSender,
                            DELETE_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0
                        )
                        // Result will be handled in onActivityResult
                        return
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error with createDeleteRequest: ${e.message}", e)
                        // Fall back to traditional method if this fails
                    }
                }
            }

            // Traditional method for older Android versions or as fallback
            songs.forEach { song ->
                try {
                    Log.d("MainActivity", "Attempting to delete song: ${song.title}")
                    Log.d("MainActivity", "Song path: ${song.path}")
                    Log.d("MainActivity", "Song ID: ${song.id}")

                    // First try to delete from MediaStore
                    val deleted = contentResolver.delete(
                        Media.EXTERNAL_CONTENT_URI,
                        "${Media._ID} = ?",
                        arrayOf(song.id.toString())
                    )
                    Log.d("MainActivity", "MediaStore deletion result: $deleted")

                    if (deleted > 0) {
                        successCount++
                        Log.d("MainActivity", "Successfully deleted song from MediaStore")
                        
                        // After successful MediaStore deletion, try to delete physical file
                val file = File(song.path)
                if (file.exists()) {
                            Log.d("MainActivity", "Physical file exists, attempting to delete")
                            if (file.delete()) {
                                Log.d("MainActivity", "Physical file deleted successfully")
                            } else {
                                Log.e("MainActivity", "Failed to delete physical file. File permissions: ${file.canRead()}, ${file.canWrite()}")
                            }
                        } else {
                            Log.d("MainActivity", "Physical file does not exist")
                        }
                    } else {
                        // If MediaStore deletion fails, try alternative method
                        val values = ContentValues().apply {
                            put(Media.IS_MUSIC, 0)
                        }
                        val updated = contentResolver.update(
                        Media.EXTERNAL_CONTENT_URI,
                            values,
                        "${Media._ID} = ?",
                        arrayOf(song.id.toString())
                    )
                        if (updated > 0) {
                            successCount++
                            Log.d("MainActivity", "Successfully marked song as non-music in MediaStore")
                        } else {
                            failureCount++
                            Log.e("MainActivity", "Failed to update MediaStore entry: ${song.id}")
                            
                            // Try to get more information about the failure
                            try {
                                val cursor = contentResolver.query(
                                    Media.EXTERNAL_CONTENT_URI,
                                    arrayOf(Media._ID, Media.DATA),
                                    "${Media._ID} = ?",
                                    arrayOf(song.id.toString()),
                                    null
                                )
                                cursor?.use {
                                    if (it.moveToFirst()) {
                                        Log.e("MainActivity", "File still exists in MediaStore. Path: ${it.getString(1)}")
                                    } else {
                                        Log.e("MainActivity", "File not found in MediaStore")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error checking MediaStore status: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    failureCount++
                    Log.e("MainActivity", "Error deleting song ${song.title}: ${e.message}", e)
                }
            }

            // Refresh the song list
            loadSongs()
            
            // Exit selection mode
            actionMode?.finish()

            // Show appropriate message
            when {
                successCount > 0 && failureCount == 0 -> {
                    Toast.makeText(this, "Successfully deleted $successCount song(s)", Toast.LENGTH_SHORT).show()
                }
                successCount > 0 && failureCount > 0 -> {
                    Toast.makeText(this, "Deleted $successCount song(s), failed to delete $failureCount", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(this, "Failed to delete songs. Please check app permissions.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in deleteSongs: ${e.message}", e)
            Toast.makeText(this, "Error deleting songs", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        val selectedSongIds = songsAdapter.getSelectedSongs()
        if (selectedSongIds.isEmpty()) return

        val selectedSongId = selectedSongIds.first()
        val song = songs.find { it.id == selectedSongId } ?: return

        val input = EditText(this).apply {
            setText(song.title)
            hint = "Enter new name"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename Song")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotEmpty()) {
                    renameSong(song, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameSong(song: Song, newTitle: String) {
        try {
            // Update MediaStore
            val values = ContentValues().apply {
                put(Media.TITLE, newTitle)
            }
            contentResolver.update(
                Media.EXTERNAL_CONTENT_URI,
                values,
                "${Media._ID} = ?",
                arrayOf(song.id.toString())
            )

            // Update local song list
            val songIndex = songs.indexOfFirst { it.id == song.id }
            if (songIndex != -1) {
                songs[songIndex] = songs[songIndex].copy(title = newTitle)
            }

            // Update current album songs if viewing album songs
            if (isViewingAlbumSongs) {
                currentAlbumSongs?.let { albumSongs ->
                    val albumSongIndex = albumSongs.indexOfFirst { it.id == song.id }
                    if (albumSongIndex != -1) {
                        albumSongs[albumSongIndex] = albumSongs[albumSongIndex].copy(title = newTitle)
                    }
                }
            }

            // Update UI
            songsAdapter.notifyDataSetChanged()
            
            // Exit selection mode
            actionMode?.finish()
            
            Toast.makeText(this, "গানের নাম পরিবর্তন করা হয়েছে", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error renaming song: ${e.message}")
            Toast.makeText(this, "গানের নাম পরিবর্তন করতে সমস্যা হচ্ছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSongs() {
        val selectedSongIds = songsAdapter.getSelectedSongs()
        if (selectedSongIds.isEmpty()) return

        try {
            val selectedSongs = songs.filter { selectedSongIds.contains(it.id) }
            if (selectedSongs.size == 1) {
                // Share single song
                val song = selectedSongs[0]
                try {
                    val file = File(song.path)
                    
                    if (!file.exists()) {
                        Toast.makeText(this, "গানটি খুঁজে পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Create content URI using FileProvider
                    val contentUri = try {
                        FileProvider.getUriForFile(
                            this,
                            "${packageName}.provider",
                            file
                        )
                    } catch (e: IllegalArgumentException) {
                        Log.e("MainActivity", "Error creating content URI: ${e.message}")
                        Toast.makeText(this, "গানটি শেয়ার করার জন্য অ্যাক্সেস পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    try {
                        startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন: ${song.title}"))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, "শেয়ার করার জন্য কোন অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Security Exception: ${e.message}")
                    Toast.makeText(this, "গানটি শেয়ার করার অনুমতি নেই", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Share multiple songs
                val uris = ArrayList<Uri>()
                var failedCount = 0
                
                selectedSongs.forEach { song ->
                    try {
                        val file = File(song.path)
                        if (!file.exists()) {
                            failedCount++
                            return@forEach
                        }

                        val uri = FileProvider.getUriForFile(
                            this,
                            "${packageName}.provider",
                            file
                        )
                        uris.add(uri)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error processing song: ${song.title}, Error: ${e.message}")
                        failedCount++
                    }
                }

                if (uris.isEmpty()) {
                    Toast.makeText(this, "কোনো গান শেয়ার করার জন্য প্রস্তুত নেই", Toast.LENGTH_SHORT).show()
                    return
                }

                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "audio/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                try {
                    val title = if (failedCount > 0) {
                        "শেয়ার করুন: ${convertToBengaliNumerals(uris.size)}টি গান (${convertToBengaliNumerals(failedCount)}টি বাদ গেছে)"
                    } else {
                        "শেয়ার করুন: ${convertToBengaliNumerals(uris.size)}টি গান"
                    }
                    startActivity(Intent.createChooser(shareIntent, title))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this, "শেয়ার করার জন্য কোন অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
            actionMode?.finish()
        } catch (e: Exception) {
            Log.e("MainActivity", "Share error: ${e.message}")
            Toast.makeText(this, "গান শেয়ার করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlaybackUI(isPlaying: Boolean) {
        this.isPlaying = isPlaying
        playPauseButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        updateBottomControlsVisibility(isPlaying)
        if (isPlaying) {
            handler.post(::updateSeekBar)
        } else {
            handler.removeCallbacks(::updateSeekBar)
        }
    }

    private fun updateBottomControlsVisibility(isPlaying: Boolean) {
        bottomPlaybackControls.visibility = if (isPlaying || currentSongPosition != -1) View.VISIBLE else View.GONE
    }

    private fun updateUnplayedCount() {
        val unplayedCount = songs.count { it.playCount == 0 }
        unplayedSongCount.text = unplayedCount.toString()
    }

    private fun updateMostPlayedCount() {
        // Count songs that have been played at least once
        val playedSongsCount = songs.count { it.playCount > 0 }
        mostPlayedCount.text = playedSongsCount.toString()
    }

    private fun updateRepeatButton() {
        val repeatIcon = when (repeatMode) {
            RepeatMode.NONE -> R.drawable.ic_repeat // No repeat
            RepeatMode.ALL -> R.drawable.ic_repeat_on // Repeat all
            RepeatMode.ONE -> R.drawable.ic_repeat_one // Repeat one
        }
        repeatButton.setImageResource(repeatIcon)
        repeatButton.setColorFilter(
            if (repeatMode != RepeatMode.NONE)
                ContextCompat.getColor(this, R.color.accent_color)
            else
                ContextCompat.getColor(this, R.color.white)
        )
    }

    override fun onResume() {
        super.onResume()
        // Check if music is playing and update controls visibility
        musicService?.let { service ->
            updateBottomControlsVisibility(service.isPlaying)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(playbackControlReceiver)
            unregisterReceiver(stateReceiver)
            unregisterReceiver(playlistUpdateReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver: ${e.message}")
        }
        
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
        equalizerManager.release()
    }

    // Public methods for fragment access
    fun getSongs(): MutableList<Song> = songs

    fun getAlbums(): MutableList<Album> = albums

    fun handleSongLongClick(song: Song): Boolean {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
            songsAdapter.toggleSelection(song.id)
            actionMode?.invalidate()
        }
        return true
    }

    fun showAlbumSongs(albumSongs: List<Song>) {
        // Switch to Songs tab
        viewPager.currentItem = 0
        
        // Update songs in SongsFragment
        (supportFragmentManager.fragments.find { it is SongsFragment } as? SongsFragment)?.let { songsFragment ->
            songsFragment.updateSongs(albumSongs)
            
            // Update the songsAdapter to use the album songs
            songsAdapter.updateSongs(albumSongs)
            
            // Store the current songs list for playback
            currentAlbumSongs = albumSongs.toMutableList()
            
            // Show back button
            isViewingAlbumSongs = true
            backButton.visibility = View.VISIBLE
            
            // Update playback button states
            updatePlaybackButtonStates()
            
            // Show toast message in Bengali
            Toast.makeText(this, "${albumSongs.size}টি গান দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getArtistFromSong(song: Song): Artist {
        val artistName = song.artist
        return artists.find { it.name == artistName } ?: Artist(
            name = artistName,
            numberOfSongs = 1,
            numberOfAlbums = 1,
            songs = mutableListOf(song),
            albums = mutableListOf(),
            imageUri = null
        )
    }

    fun getArtists(): MutableList<Artist> {
        val artistMap = mutableMapOf<String, Artist>()
        
        songs.forEach { song ->
            val artistName = song.artist
            val artist = artistMap.getOrPut(artistName) {
                Artist(
                    name = artistName,
                    numberOfSongs = 0,
                    numberOfAlbums = 0,
                    songs = mutableListOf(),
                    albums = mutableListOf(),
                    imageUri = null
                )
            }
            
            artist.songs.add(song)
            artist.numberOfSongs = artist.songs.size
            
            // Update albums
            val albumName = song.album
            val existingAlbum = artist.albums.find { it.name == albumName }
            if (existingAlbum == null) {
                // Create new album with mutable list
                val songsList = mutableListOf<Song>()
                songsList.add(song)
                val newAlbum = Album(
                    id = song.id,
                    name = albumName,
                    artist = song.artist,
                    albumArtUri = song.albumArtUri ?: "content://media/external/audio/albumart/${song.id}",
                    numberOfSongs = 1,
                    songs = songsList
                )
                artist.albums.add(newAlbum)
                artist.numberOfAlbums = artist.albums.size
            } else {
                // Add song to existing album
                existingAlbum.songs.add(song)
            }
        }
        
        return artistMap.values.toMutableList().apply {
            sortBy { it.name }
        }
    }

    fun showArtistSongs(songs: List<Song>) {
        // Update the songs list and notify the adapter
        songsFragment?.let {
            it.updateSongs(songs)
            viewPager.setCurrentItem(0, true) // Switch to Songs tab
            
            // Store the current songs list for playback
            currentAlbumSongs = songs.toMutableList()
            
            // Show back button
            isViewingAlbumSongs = true
            backButton.visibility = View.VISIBLE
            
            // Update playback button states
            updatePlaybackButtonStates()
            
            // Show toast message in Bengali
            Toast.makeText(this, "${songs.size}টি গান দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
        }
    }

    fun showFolderSongs(folder: Folder) {
        viewPager.currentItem = 0  // Switch to Songs tab
        
        // Update the songs list and notify the adapter
        songsFragment.updateSongs(folder.songs)
        
        // Store the current songs list for playback
        currentAlbumSongs = folder.songs.toMutableList()
        
        // Show back button
        isViewingAlbumSongs = true
        backButton.visibility = View.VISIBLE
        
        // Update playback button states
        updatePlaybackButtonStates()
        
        // Show toast message in Bengali
        Toast.makeText(this, "${folder.name} থেকে ${folder.songs.size}টি গান দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
    }

    private fun showAllSongs() {
        isViewingAlbumSongs = false
        backButton.visibility = View.GONE
        currentAlbumSongs = null
        
        // Update the SongsFragment
        songsFragment.updateSongs(songs)
        
        // Update playback button states
        updatePlaybackButtonStates()
        
        // Show toast message in Bengali
        Toast.makeText(this, "সব গান দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
    }

    private fun setupViewPager() {
        // Initialize fragments
        songsFragment = SongsFragment()
        albumsFragment = AlbumsFragment()
        artistsFragment = ArtistsFragment()
        foldersFragment = FoldersFragment()

        viewPager.apply {
            adapter = object : FragmentStateAdapter(this@MainActivity) {
                override fun getItemCount(): Int = 4  // Updated to include Folders tab
                override fun createFragment(position: Int): Fragment {
                    return when (position) {
                        0 -> songsFragment
                        1 -> albumsFragment
                        2 -> artistsFragment
                        3 -> foldersFragment
                        else -> throw IllegalArgumentException("Invalid position $position")
                    }
                }
            }
        }

        TabLayoutMediator(topNavigation, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Songs"
                1 -> "Albums"
                2 -> "Artists"
                3 -> "Folders"
                else -> throw IllegalArgumentException("Invalid position $position")
            }
        }.attach()
    }

    private fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> {
                repeatButton.setImageResource(R.drawable.ic_repeat)
                repeatButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
                Toast.makeText(this, "সব গান রিপিট হবে", Toast.LENGTH_SHORT).show()
                RepeatMode.ALL
            }
            RepeatMode.ALL -> {
                repeatButton.setImageResource(R.drawable.ic_repeat_one)
                Toast.makeText(this, "বর্তমান গান রিপিট হবে", Toast.LENGTH_SHORT).show()
                RepeatMode.ONE
            }
            RepeatMode.ONE -> {
                repeatButton.setImageResource(R.drawable.ic_repeat)
                repeatButton.clearColorFilter()
                Toast.makeText(this, "রিপিট বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
                RepeatMode.NONE
            }
        }
        musicService?.setRepeatMode(repeatMode)
        updateRepeatButton()
        updatePlaybackButtonStates() // Update button states when repeat mode changes
    }

    private fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        if (isShuffleEnabled) {
            shuffleButton.setImageResource(R.drawable.ic_shuffle)
            shuffleButton.setColorFilter(ContextCompat.getColor(this, R.color.accent_color))
            generateShuffleIndices()
            Toast.makeText(this, "শাফল চালু করা হয়েছে", Toast.LENGTH_SHORT).show()
        } else {
            shuffleButton.setImageResource(R.drawable.ic_shuffle)
            shuffleButton.clearColorFilter()
            shuffledIndices.clear()
            currentShuffleIndex = -1
            Toast.makeText(this, "শাফল বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
        }
        musicService?.setShuffleEnabled(isShuffleEnabled)
        updatePlaybackButtonStates()
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            if (isViewingAlbumSongs) {
                showAllSongs()
            }
        }
    }

    override fun onBackPressed() {
        if (songsAdapter.isInSelectionMode()) {
            // Clear selection and exit selection mode
            exitSelectionMode()
            return
        }
        when {
            searchBar.text.isNotEmpty() -> {
                // Clear search and show all songs
                searchBar.text.clear()
                songsAdapter.updateSongs(songs)
            }
            isViewingAlbumSongs -> {
                showAllSongs()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }

    private fun exitSelectionMode() {
        songsAdapter.setSelectionMode(false)
        songsAdapter.clearSelection()
        actionMode?.finish()
        actionMode = null
        isSelectionMode = false
        notifySelectionModeChanged()
    }

    private fun notifySelectionModeChanged() {
        // Force refresh the current view
        when (viewPager.currentItem) {
            0 -> songsFragment.updateSongs(songs)
            1 -> albumsFragment.updateAlbums(albums)
            2 -> artistsFragment.updateArtists(artists)
            3 -> foldersFragment.updateFolders(folders)
        }
    }

    private fun loadAlbums() {
        organizeAlbums()
        albumsAdapter.updateAlbums(albums)
        currentView = ViewType.ALBUMS
    }

    private fun loadArtists() {
        artists = getArtists()
        (supportFragmentManager.fragments.find { it is ArtistsFragment } as? ArtistsFragment)?.updateArtists(artists)
        currentView = ViewType.ARTISTS
    }

    private fun loadFolders() {
        // Folders are already loaded during loadSongs()
        (supportFragmentManager.fragments.find { it is FoldersFragment } as? FoldersFragment)?.updateFolders(folders)
        currentView = ViewType.FOLDERS
    }

    private fun updatePlaylistCount() {
        try {
            val playlists = playlistManager.getAllPlaylists()
            playlistCount.text = playlists.size.toString()
            
            // Update the playlist card visibility based on count
            findViewById<MaterialCardView>(R.id.playList)?.alpha = if (playlists.isEmpty()) 0.5f else 1.0f
            
            // Log for debugging
            Log.d("MainActivity", "Playlist count updated: ${playlists.size}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating playlist count: ${e.message}", e)
        }
    }

    // Update this method to be called when playlists are modified
    fun refreshPlaylistCount() {
        updatePlaylistCount()
    }

    private fun getPlaylists(): List<Playlist> {
        return playlistManager.getAllPlaylists()
    }

    companion object {
        private const val DELETE_REQUEST_CODE = 1001
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DELETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Log.d("MainActivity", "Delete request approved by user")
                Toast.makeText(this, "Songs deleted successfully", Toast.LENGTH_SHORT).show()
                loadSongs() // Refresh the song list
                actionMode?.finish() // Exit selection mode
            } else {
                Log.d("MainActivity", "Delete request rejected by user or failed")
                Toast.makeText(this, "Song deletion cancelled or failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateShuffleIndices() {
        val songsToPlay = currentAlbumSongs ?: songs
        shuffledIndices = (0 until songsToPlay.size).toMutableList()
        shuffledIndices.shuffle()
        currentShuffleIndex = shuffledIndices.indexOf(currentSongPosition)
    }
}