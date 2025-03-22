package com.cap.ultimatemusicplayer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.Slider
import java.io.Serializable

class SongDetailsActivity : AppCompatActivity(), SeekBar.OnSeekBarChangeListener {
    private lateinit var backButton: ImageButton
    private lateinit var albumArtLarge: ImageView
    private lateinit var songInfo: TextView
    private lateinit var artistNameDetails: TextView
    private lateinit var seekBarDetails: SeekBar
    private lateinit var currentTimeDetails: TextView
    private lateinit var totalTimeDetails: TextView
    private lateinit var listButton: ImageButton
    private lateinit var editButton: ImageButton
    private lateinit var favoriteButton: ImageButton
    private lateinit var equalizerButton: ImageButton
    private lateinit var timerButton: ImageButton
    private lateinit var volumeButton: ImageButton
    private lateinit var shuffleButtonDetails: ImageButton
    private lateinit var previousButtonDetails: ImageButton
    private lateinit var playPauseButtonDetails: FloatingActionButton
    private lateinit var nextButtonDetails: ImageButton
    private lateinit var repeatButtonDetails: ImageButton
    
    // Song information variables
    private var currentSongId: Long = -1L
    private var songTitle: String = "Unknown Title"
    private var songArtist: String = "Unknown Artist"
    private var songAlbum: String = "Unknown Album"
    private var albumArtUri: String? = null
    private var isPlaying: Boolean = false
    private var isFavorite: Boolean = false
    private var isShuffleEnabled: Boolean = false
    private var isMuted: Boolean = false
    private var repeatMode: Int = 0
    private var currentSpeed: Float = 1.0f
    private val SPEED_EPSILON = 0.01f
    
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var remainingTime: Long = 0
    private var currentPosition = 0
    private var duration = 0
    private var isTrackingTouch = false
    private val updateSeekBarHandler = Handler(Looper.getMainLooper())
    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (!isTrackingTouch && isPlaying) {
                // Send broadcast to get current position
                sendControlCommand("GET_CURRENT_POSITION")
            }
            updateSeekBarHandler.postDelayed(this, 1000)
        }
    }
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var equalizerManager: EqualizerManager
    private var equalizerDialog: AlertDialog? = null
    private val equalizerSetupHandler = Handler(Looper.getMainLooper())
    private val equalizerSetupRunnable = object : Runnable {
        override fun run() {
            if (!equalizerManager.isAvailable() && equalizerSetupAttempts < MAX_EQUALIZER_SETUP_ATTEMPTS) {
                requestAudioSessionId()
                equalizerSetupAttempts++
                equalizerSetupHandler.postDelayed(this, 1000) // Try again after 1 second
            }
        }
    }
    private var equalizerSetupAttempts = 0
    private val MAX_EQUALIZER_SETUP_ATTEMPTS = 3
    
    // Variables to track playlist context
    private var isFromPlaylist = false
    private var playlistId = -1L
    private var playlistName = ""

    private val playbackStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE") {
                // Extract playback state from intent
                isPlaying = intent.getBooleanExtra("is_playing", false)
                isShuffleEnabled = intent.getBooleanExtra("is_shuffle_enabled", false)
                repeatMode = intent.getIntExtra("repeat_mode", 0)
                currentSpeed = intent.getFloatExtra("playback_speed", 1.0f)
                
                Log.d("SongDetailsActivity", "Playback state updated: isPlaying=$isPlaying, shuffle=$isShuffleEnabled, repeat=$repeatMode, speed=$currentSpeed")
                
                // Update UI to reflect current state
                updatePlaybackControls()
                updateSpeedButtonColor()
                
                // Update seekbar if included in this update
                if (intent.hasExtra("current_position") && intent.hasExtra("total_duration")) {
                    val currentPosition = intent.getIntExtra("current_position", 0)
                    val totalDuration = intent.getIntExtra("total_duration", 0)
                    
                    // Update seekbar and time displays
                    if (!isTrackingTouch) {
                        seekBarDetails.max = totalDuration
                        seekBarDetails.progress = currentPosition
                    }
                    
                    currentTimeDetails.text = formatTime(currentPosition)
                    totalTimeDetails.text = formatTime(totalDuration)
                }
            } else if (intent?.action == "com.cap.ultimatemusicplayer.SEEKBAR_UPDATE") {
                // This is just a seekbar position update, don't update playback controls
                try {
                    // Try to get values as Integer first (which is what the service is sending based on the error)
                    val currentPosition = intent.getIntExtra("current_position", 0)
                    val duration = intent.getIntExtra("duration", 0)
                    
                    // Log received values for debugging
                    Log.d("SongDetailsActivity", "Received seekbar update: position=$currentPosition, duration=$duration")
                    
                    // Ensure UI updates happen on the main thread
                    runOnUiThread {
                        if (!isTrackingTouch) {
                            if (duration > 0) {
                                seekBarDetails.max = duration
                                seekBarDetails.progress = currentPosition
                            } else {
                                Log.e("SongDetailsActivity", "Invalid duration: $duration")
                            }
                            
                            currentTimeDetails.text = formatTime(currentPosition)
                            totalTimeDetails.text = formatTime(duration)
                            
                            // Log the actual values being set
                            Log.d("SongDetailsActivity", "Updated seekbar: progress=${seekBarDetails.progress}, max=${seekBarDetails.max}")
                        } else {
                            Log.d("SongDetailsActivity", "Skipping seekbar update due to user interaction")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SongDetailsActivity", "Error handling seekbar update: ${e.message}")
                }
            }
        }
    }

    private val playbackErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.PLAYBACK_ERROR") {
                val errorMessage = intent.getStringExtra("error_message") ?: "Unknown error"
                runOnUiThread {
                    Toast.makeText(this@SongDetailsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    // Reset UI state
                    isPlaying = false
                    updatePlaybackControls()
                }
            }
        }
    }

    private val songChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.SONG_CHANGED") {
                val songId = intent.getLongExtra("song_id", -1L)
                val songTitle = intent.getStringExtra("song_title")
                val songArtist = intent.getStringExtra("song_artist")
                val songAlbumArtUri = intent.getStringExtra("song_album_art_uri") 
                    ?: intent.getStringExtra("album_art_uri") // Try both variations
                
                Log.d("SongDetailsActivity", "Received song change: ID=$songId, Title=$songTitle, Artist=$songArtist, AlbumArt=$songAlbumArtUri")
                
                // Update song details in the UI
                runOnUiThread {
                    if (songTitle != null && songArtist != null) {
                        updateSongInfo(songTitle, songArtist, songAlbumArtUri)
                    }
                }
                
                // Update playback state if included
                if (intent.hasExtra("is_playing")) {
                    isPlaying = intent.getBooleanExtra("is_playing", false)
                    updatePlaybackControls()
                }
            }
        }
    }

    private val favoriteStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.FAVORITE_STATUS_UPDATE") {
                val songId = intent.getLongExtra("song_id", -1L)
                if (songId == currentSongId) {
                    isFavorite = intent.getBooleanExtra("is_favorite", false)
                    favoriteButton.setImageResource(
                        if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                    )
                }
            }
        }
    }

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.cap.ultimatemusicplayer.AUDIO_SESSION_ID") {
                val audioSessionId = intent.getIntExtra("audioSessionId", 0)
                val isMediaPlayerReady = intent.getBooleanExtra("isMediaPlayerReady", false)  // Default to false
                val isTemporary = intent.getBooleanExtra("isTemporary", false)
                val error = intent.getStringExtra("error")
                
                Log.d("Equalizer", "Received audio session ID: $audioSessionId, MediaPlayer ready: $isMediaPlayerReady, isTemporary: $isTemporary")
                
                if (audioSessionId != 0 && isMediaPlayerReady) {
                    try {
                        // Release any existing equalizer first
                        Log.d("Equalizer", "Releasing existing equalizer before setup")
                        equalizerManager.release()
                        
                        // Set up new equalizer
                        Log.d("Equalizer", "Setting up equalizer with session ID: $audioSessionId")
                        equalizerManager.setupEqualizer(audioSessionId)
                        
                        // Verify equalizer was set up correctly
                        if (equalizerManager.isAvailable()) {
                            Log.d("Equalizer", "Equalizer setup successful")
                            
                            // Stop retrying since setup was successful
                            equalizerSetupHandler.removeCallbacks(equalizerSetupRunnable)
                            equalizerSetupAttempts = 0
                            
                            // Update UI to show equalizer is ready
                            runOnUiThread {
                                equalizerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.white))
                                
                                // If dialog is already showing, update it
                                if (equalizerDialog?.isShowing == true) {
                                    equalizerDialog?.dismiss()
                                    showEqualizerDialog()
                                }
                                
                                // Force open dialog if it was requested but not shown
                                if (equalizerDialogRequested) {
                                    equalizerDialogRequested = false
                                    showEqualizerDialog()
                                }
                            }
                        } else {
                            throw Exception("Equalizer setup failed - not available after setup")
                        }
                    } catch (e: Exception) {
                        Log.e("Equalizer", "Failed to set up equalizer: ${e.message}")
                        e.printStackTrace()
                        
                        // Retry if we haven't exceeded max attempts
                        if (equalizerSetupAttempts < MAX_EQUALIZER_SETUP_ATTEMPTS) {
                            equalizerSetupAttempts++
                            equalizerSetupHandler.postDelayed(equalizerSetupRunnable, 1000)
                            
                            runOnUiThread {
                                Toast.makeText(
                                    this@SongDetailsActivity,
                                    "Failed to set up equalizer: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                equalizerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.white))
                            }
                        } else {
                            handleEqualizerSetupError("Max retry attempts reached")
                        }
                    }
                } else {
                    // Handle error or retry
                    val errorMsg = error ?: "Unknown error"
                    Log.e("Equalizer", "Invalid audio session ID or MediaPlayer not ready: $errorMsg")
                    
                    // Retry if we haven't exceeded max attempts
                    if (equalizerSetupAttempts < MAX_EQUALIZER_SETUP_ATTEMPTS) {
                        equalizerSetupAttempts++
                        equalizerSetupHandler.postDelayed(equalizerSetupRunnable, 1000)
                        
                        runOnUiThread {
                            Toast.makeText(
                                this@SongDetailsActivity,
                                "Failed to set up equalizer",
                                Toast.LENGTH_SHORT
                            ).show()
                            equalizerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.white))
                        }
                    } else {
                        handleEqualizerSetupError("Max retry attempts reached")
                    }
                }
            }
        }
    }

    private fun handleEqualizerSetupError(error: String) {
        runOnUiThread {
            Toast.makeText(
                this@SongDetailsActivity,
                "Failed to set up equalizer: $error",
                Toast.LENGTH_SHORT
            ).show()
            equalizerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.white))
        }
    }

    private lateinit var musicService: MusicService
    private var isBound = false

    private val musicServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = (service as MusicService.MusicBinder).getService()
            isBound = true
            onBindingComplete()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_song_details)
        
        // Initialize views
        initializeViews()
        
        // Setup equalizer manager
        equalizerManager = EqualizerManager(this)
        
        // Setup favorites manager
        favoritesManager = FavoritesManager(this)
        
        // Load song details right away from intent
        loadSongDetails()
        
        // Register broadcast receivers
        registerReceivers()
        
        // Bind to MusicService
        val serviceIntent = Intent(this, MusicService::class.java)
        bindService(serviceIntent, musicServiceConnection, Context.BIND_AUTO_CREATE)
        
        // Set up click listeners
        setupClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
        updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable)
        equalizerSetupHandler.removeCallbacks(equalizerSetupRunnable)
        unregisterReceiver(playbackStateReceiver)
        unregisterReceiver(playbackErrorReceiver)
        unregisterReceiver(songChangeReceiver)
        unregisterReceiver(favoriteStatusReceiver)
        unregisterReceiver(audioSessionReceiver)
        equalizerManager.release()
        equalizerDialog?.dismiss()
        equalizerDialog = null
        unbindService(musicServiceConnection)
    }

    private fun initializeViews() {
        backButton = findViewById(R.id.backButton)
        albumArtLarge = findViewById(R.id.albumArtLarge)
        songInfo = findViewById(R.id.songInfo)
        artistNameDetails = findViewById(R.id.artistNameDetails)
        seekBarDetails = findViewById(R.id.seekBarDetails)
        currentTimeDetails = findViewById(R.id.currentTimeDetails)
        totalTimeDetails = findViewById(R.id.totalTimeDetails)
        listButton = findViewById(R.id.listButton)
        editButton = findViewById(R.id.playbackSpeed)
        favoriteButton = findViewById(R.id.favoriteButton)
        equalizerButton = findViewById(R.id.equalizerButton)
        timerButton = findViewById(R.id.timerButton)
        volumeButton = findViewById(R.id.volumeButton)
        shuffleButtonDetails = findViewById(R.id.shuffleButtonDetails)
        previousButtonDetails = findViewById(R.id.previousButtonDetails)
        playPauseButtonDetails = findViewById(R.id.playPauseButtonDetails)
        nextButtonDetails = findViewById(R.id.nextButtonDetails)
        repeatButtonDetails = findViewById(R.id.repeatButtonDetails)
        
        // Setup seekbar listener
        seekBarDetails.setOnSeekBarChangeListener(this)
        
        // Initialize editButton (playback speed)
        editButton.setImageResource(R.drawable.ic_playbackspeed)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        playPauseButtonDetails.setOnClickListener {
            sendControlCommand("TOGGLE_PLAY_PAUSE")
        }

        previousButtonDetails.setOnClickListener {
            if (isBound) {
                musicService.playPreviousInPlaylist()
            } else {
                sendControlCommand("PLAY_PREVIOUS")
            }
        }

        nextButtonDetails.setOnClickListener {
            if (isBound) {
                musicService.playNextInPlaylist()
            } else {
                sendControlCommand("PLAY_NEXT")
            }
        }

        shuffleButtonDetails.setOnClickListener {
            // Toggle local state immediately for better responsiveness
            isShuffleEnabled = !isShuffleEnabled
            updateShuffleButtonState()
            
            // Send command to service
            Log.d("ShuffleControl", "Toggling shuffle mode to: $isShuffleEnabled")
            sendControlCommand("TOGGLE_SHUFFLE")
        }

        repeatButtonDetails.setOnClickListener {
            sendControlCommand("CHANGE_REPEAT_MODE")
        }
        
        listButton.setOnClickListener {
            sendControlCommand("GET_SONG_LIST")
            showSongListBottomSheet()
        }
        
        editButton.setOnClickListener {
            showSpeedControlDialog()
        }
        
        favoriteButton.setOnClickListener {
            if (currentSongId != -1L) {
                // Toggle favorite status
                isFavorite = !isFavorite
                updateFavoriteButtonState()
                
                // Send command to update favorite status
                val command = if (isFavorite) "ADD_TO_FAVORITE" else "REMOVE_FROM_FAVORITE"
                val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
                    setPackage(packageName)
                    putExtra("command", command)
                    putExtra("song_id", currentSongId)
                }
                sendBroadcast(intent)
            }
        }
        
        equalizerButton.setOnClickListener {
            showEqualizerDialog()
            // Set the button color to accent when dialog is shown
            equalizerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.accent_color))
        }
        
        timerButton.setOnClickListener {
            showTimerDialog()
        }
        
        volumeButton.setOnClickListener {
            // Send command to service without toggling local state
            // Let the broadcast receiver update the UI when it gets the state update
            val command = if (isMuted) "UNMUTE" else "MUTE"
            Log.d("VolumeControl", "Sending command: $command, current muted state: $isMuted")
            sendControlCommand(command)
            
            // Update local state and UI immediately for better responsiveness
            isMuted = !isMuted
            updateVolumeButtonState()
        }
    }
    

    private fun showSpeedControlDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x")
        val speedValues = arrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        
        // Find current speed index with epsilon comparison
        val currentSpeedIndex = speedValues.indexOfFirst { speed ->
            kotlin.math.abs(speed - currentSpeed) < SPEED_EPSILON
        }.takeIf { it != -1 } ?: 2  // Default to 1.0x (index 2) if not found
        
        Log.d("SpeedControl", "Opening dialog with current speed: $currentSpeed, index: $currentSpeedIndex")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(speeds, currentSpeedIndex) { dialog, which ->
                val selectedSpeed = speedValues[which]
                
                // Only update if speed actually changed
                if (kotlin.math.abs(currentSpeed - selectedSpeed) > SPEED_EPSILON) {
                    Log.d("SpeedControl", "Speed changed from $currentSpeed to $selectedSpeed")
                    
                    // Update current speed before sending command
                currentSpeed = selectedSpeed
                
                    // Update button color immediately
                    updateSpeedButtonColor()
                    
                    // Send command to update playback speed
                    val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
                        setPackage(packageName)
                        putExtra("command", "SET_SPEED")
                        putExtra("speed", selectedSpeed)
                    }
                    sendBroadcast(intent)
                    
                    // Show toast with selected speed
                Toast.makeText(this@SongDetailsActivity, "Playback speed: ${speeds[which]}", Toast.LENGTH_SHORT).show()
                }
                
                dialog.dismiss()
            }
            .show()
    }

    private fun updateSpeedButtonColor() {
        try {
            val isNormalSpeed = kotlin.math.abs(currentSpeed - 1.0f) < SPEED_EPSILON
            val color = if (!isNormalSpeed) {
                ContextCompat.getColor(this@SongDetailsActivity, R.color.accent_color)
            } else {
                ContextCompat.getColor(this@SongDetailsActivity, R.color.white)
            }
            
            Log.d("SpeedControl", "Updating speed button: Speed=$currentSpeed, isNormal=$isNormalSpeed, color=${if (!isNormalSpeed) "accent" else "white"}")
            
            runOnUiThread {
                editButton.setImageResource(R.drawable.ic_playbackspeed)
                editButton.clearColorFilter()
                editButton.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
                editButton.invalidate()
            }
        } catch (e: Exception) {
            Log.e("SpeedControl", "Error updating speed button color: ${e.message}")
        }
    }

    private fun sendControlCommand(command: String, speed: Float? = null) {
        try {
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
            setPackage(packageName)
            putExtra("command", command)
            speed?.let { putExtra("speed", it) }
        }
        sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("SongDetailsActivity", "Error sending control command: ${e.message}")
            Toast.makeText(this@SongDetailsActivity, "Error controlling playback", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSongDetails() {
        // Get song details from the intent
        currentSongId = intent.getLongExtra("song_id", -1L)
        songTitle = intent.getStringExtra("song_title") ?: "Unknown Title"
        songArtist = intent.getStringExtra("song_artist") ?: "Unknown Artist"
        songAlbum = intent.getStringExtra("song_album") ?: "Unknown Album"
        albumArtUri = intent.getStringExtra("song_album_art_uri") // Fix the key to match PlaylistDetailActivity
        
        Log.d("SongDetailsActivity", "Loading song details: ID=$currentSongId, Title=$songTitle, Artist=$songArtist, AlbumArt=$albumArtUri")
        
        // Update the UI with song details
        updateSongInfo(songTitle, songArtist, albumArtUri)
        
        // Check if we should start playing immediately
        isPlaying = intent.getBooleanExtra("is_playing", false)
        updatePlaybackControls()
        
        // Request current playback state from service
        requestPlaybackState()
        
        // Update favorite button state
        updateFavoriteButtonState()
    }

    private fun showTimerDialog() {
        val options = arrayOf("5 minutes", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "Cancel Timer")
        val times = arrayOf(5L, 10L, 15L, 30L, 45L, 60L, 0L) // in minutes

        MaterialAlertDialogBuilder(this)
            .setTitle("Set Sleep Timer")
            .setItems(options) { _, which ->
                val selectedTime = times[which]
                if (selectedTime == 0L) {
                    cancelTimer()
                    Toast.makeText(this@SongDetailsActivity, "Timer cancelled", Toast.LENGTH_SHORT).show()
                } else {
                    setTimer(selectedTime)
                    Toast.makeText(this@SongDetailsActivity, "Timer set for $selectedTime minutes", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun setTimer(minutes: Long) {
        // Cancel any existing timer
        cancelTimer()

        // Convert minutes to milliseconds
        remainingTime = minutes * 60 * 1000

        // Set volume and timer buttons color to green when timer is active
        timerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.accent_color))

        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (remainingTime > 0) {
                    remainingTime -= 1000 // Decrease by 1 second
                    timerHandler?.postDelayed(this, 1000)
                } else {
                    // Time's up - pause the song and reset button colors
                    sendControlCommand("TOGGLE_PLAY_PAUSE")
                    cancelTimer()
                }
            }
        }
        timerHandler?.postDelayed(timerRunnable!!, 100)
    }

    private fun cancelTimer() {
        timerRunnable?.let { timerHandler?.removeCallbacks(it) }
        timerHandler = null
        timerRunnable = null
        remainingTime = 0
        // Reset both volume and timer buttons color to white when timer is cancelled
        volumeButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.white))
        timerButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, R.color.white))
    }

    private fun showSongListBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_song_list, null)
        bottomSheetDialog.setContentView(view)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.songListRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // Set fixed height for the RecyclerView
        val displayMetrics = resources.displayMetrics
        val height = (displayMetrics.heightPixels * 2) / 3
        recyclerView.layoutParams.height = height
        
        // Enable scrollbars
        recyclerView.isVerticalScrollBarEnabled = true
        recyclerView.setHasFixedSize(true)
        recyclerView.clipToPadding = false
        recyclerView.setPadding(0, 0, 0, 16)
        
        val adapter = SongsAdapter(
            songs = emptyList(),
            onSongClick = { song ->
                // Send broadcast to play selected song
                val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
                    setPackage(packageName)
                    putExtra("command", "PLAY_SONG")
                    putExtra("song_id", song.id)
                }
                sendBroadcast(intent)
                bottomSheetDialog.dismiss()
            },
            onSongLongClick = { _ -> false } // No long click action needed in bottom sheet
        )
        recyclerView.adapter = adapter
        
        // Register a temporary receiver for song list
        val songListReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.cap.ultimatemusicplayer.SONG_LIST_UPDATE") {
                    val songs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayListExtra("songs", Song::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableArrayListExtra("songs")
                    }
                    
                    songs?.let {
                        adapter.updateSongs(it)
                        Toast.makeText(this@SongDetailsActivity, "Got ${it.size} songs", Toast.LENGTH_SHORT).show()
                        recyclerView.scrollToPosition(0)
                    } ?: Toast.makeText(this@SongDetailsActivity, "No songs found", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        try {
            // Register receiver with RECEIVER_NOT_EXPORTED flag
            registerReceiver(
                songListReceiver,
                IntentFilter("com.cap.ultimatemusicplayer.SONG_LIST_UPDATE"),
                Context.RECEIVER_NOT_EXPORTED
            )
            
            bottomSheetDialog.setOnDismissListener {
                try {
                    unregisterReceiver(songListReceiver)
                } catch (e: Exception) {
                    // Ignore if receiver is already unregistered
                }
            }
            
            // Show dialog first, then request songs
            bottomSheetDialog.show()
            
            // Request song list after showing dialog
            Handler(Looper.getMainLooper()).postDelayed({
                sendControlCommand("GET_SONG_LIST")
                Toast.makeText(this@SongDetailsActivity, "Loading song list...", Toast.LENGTH_SHORT).show()
            }, 100)
            
        } catch (e: Exception) {
            Toast.makeText(this@SongDetailsActivity, "Failed to load song list", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }
    }

    private fun updateFavoriteButtonState() {
        val color = if (isFavorite) R.color.accent_color else R.color.white
        favoriteButton.setImageResource(if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
        favoriteButton.setColorFilter(ContextCompat.getColor(this@SongDetailsActivity, color))
    }

    // SeekBar.OnSeekBarChangeListener implementations
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser) {
            currentTimeDetails.text = formatTime(progress)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        isTrackingTouch = true
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        isTrackingTouch = false
        seekBar?.let {
            // Send seek command to MainActivity
            val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
                setPackage(packageName)
                putExtra("command", "SEEK_TO")
                putExtra("position", it.progress)
            }
            sendBroadcast(intent)
            
            // Update the time display immediately
            currentTimeDetails.text = formatTime(it.progress)
        }
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun updateSongInfo(title: String?, artist: String?, albumArtUri: String?) {
        // Update text views
        songInfo.text = title ?: "Unknown Title"
        artistNameDetails.text = artist ?: "Unknown Artist"
        
        // Log album art loading to debug
        Log.d("SongDetailsActivity", "Loading album art from URI: $albumArtUri")
        
        // Load album art
        try {
            if (albumArtUri != null && albumArtUri.isNotEmpty()) {
                Glide.with(this)
                    .load(albumArtUri)
                    .placeholder(R.drawable.ic_music_note)
                    .error(R.drawable.ic_music_note)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)  // Cache the image
                    .centerCrop()
                    .into(albumArtLarge)
                
                Log.d("SongDetailsActivity", "Started loading album art from URI: $albumArtUri")
            } else {
                Log.d("SongDetailsActivity", "No album art URI provided, using default image")
                albumArtLarge.setImageResource(R.drawable.ic_music_note)
            }
        } catch (e: Exception) {
            Log.e("SongDetailsActivity", "Error loading album art: ${e.message}")
            albumArtLarge.setImageResource(R.drawable.ic_music_note)
        }
    }
    
    private var equalizerDialogRequested = false
    private var equalizerTempMediaPlayer: MediaPlayer? = null
    
    private fun showEqualizerDialog() {
        // Add debug logs to track the flow
        Log.d("Equalizer", "showEqualizerDialog called, checking availability")
        
        // Create a direct equalizer instance for the dialog
        if (!equalizerManager.isAvailable()) {
            Log.d("Equalizer", "Equalizer not available, creating direct equalizer")
            
            try {
                // First try to get the audio session ID from the music service
                val serviceIntent = Intent(this, MusicService::class.java).apply {
                    action = "GET_AUDIO_SESSION_ID"
                }
                startService(serviceIntent)
                Log.d("Equalizer", "Sent request for audio session ID from music service")
                
                // Create a temporary MediaPlayer to get a valid audio session ID as backup
                val tempMediaPlayer = MediaPlayer()
                tempMediaPlayer.setVolume(0f, 0f) // Mute it
                
                // Try to initialize with a dummy source
                try {
                    val assetFileDescriptor = assets.openFd("dummy_silence.mp3")
                    tempMediaPlayer.setDataSource(
                        assetFileDescriptor.fileDescriptor,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.length
                    )
                    tempMediaPlayer.prepare()
                    Log.d("Equalizer", "Temp MediaPlayer prepared successfully")
                } catch (e: Exception) {
                    Log.e("Equalizer", "Error preparing temp MediaPlayer: ${e.message}")
                    // Continue anyway, we might still get a valid session ID
                }
                
                val sessionId = tempMediaPlayer.audioSessionId
                Log.d("Equalizer", "Created temp MediaPlayer with session ID: $sessionId")
                
                if (sessionId != 0) {
                    // Set up the equalizer with this session ID
                    equalizerManager.setupEqualizer(sessionId)
                    
                    // Keep the MediaPlayer alive while the dialog is open
                    tempMediaPlayer.setOnCompletionListener {
                        it.start() // Loop it to keep it alive
                    }
                    tempMediaPlayer.start()
                    
                    // Store the temp MediaPlayer to release it later
                    equalizerTempMediaPlayer = tempMediaPlayer
                    
                    // If equalizer is now available, show the dialog
                    if (equalizerManager.isAvailable()) {
                        Log.d("Equalizer", "Direct equalizer setup successful, showing dialog")
                        createAndShowEqualizerDialog()
            return
                    }
                } else {
                    // Clean up if we couldn't get a valid session ID
                    tempMediaPlayer.release()
                }
            } catch (e: Exception) {
                Log.e("Equalizer", "Error creating direct equalizer: ${e.message}")
                e.printStackTrace()
            }
            
            // If we got here, the direct approach failed
            Toast.makeText(this@SongDetailsActivity, "Setting up equalizer, please wait...", Toast.LENGTH_SHORT).show()
            
            // Set flag to indicate dialog was requested
            equalizerDialogRequested = true
            
            // Try the normal approach as fallback
            equalizerSetupAttempts = 0
            equalizerSetupHandler.removeCallbacks(equalizerSetupRunnable)
            requestAudioSessionId()
            
            // Schedule multiple retries with increasing delays
            scheduleEqualizerRetries()
            
            return
        }

        // If equalizer is available, show the dialog
        createAndShowEqualizerDialog()
    }
    
    private fun scheduleEqualizerRetries() {
        // First retry after 2 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d("Equalizer", "First retry check - Checking equalizer availability")
            if (equalizerManager.isAvailable()) {
                Log.d("Equalizer", "First retry - Equalizer is now available, showing dialog")
                showEqualizerDialog()
            } else {
                Log.d("Equalizer", "First retry - Equalizer still not available")
                
                // Second retry after 5 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d("Equalizer", "Second retry check - Checking equalizer availability")
                    if (equalizerManager.isAvailable()) {
                        Log.d("Equalizer", "Second retry - Equalizer is now available, showing dialog")
                        showEqualizerDialog()
                    } else {
                        Log.d("Equalizer", "Second retry - Equalizer still not available")
                        
                        // Last attempt - force a complete reset and try again
                        equalizerManager.release()
                        requestAudioSessionId()
                        
                        // Final retry after 3 more seconds
                        Handler(Looper.getMainLooper()).postDelayed({
                            Log.d("Equalizer", "Final retry check - Checking equalizer availability")
                            if (equalizerManager.isAvailable()) {
                                Log.d("Equalizer", "Final retry - Equalizer is now available, showing dialog")
                                showEqualizerDialog()
                            } else {
                                Log.d("Equalizer", "Final retry - Equalizer setup failed after multiple attempts")
                                Toast.makeText(this@SongDetailsActivity, "Failed to set up equalizer after multiple attempts", Toast.LENGTH_LONG).show()
                                equalizerDialogRequested = false
                            }
                        }, 3000)
                    }
                }, 3000)
            }
        }, 2000)
    }

    private fun createAndShowEqualizerDialog() {
        try {
            // Create the dialog
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setTitle("Equalizer")
            
            // Inflate the layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_equalizer, null)
            dialogBuilder.setView(dialogView)
            
            // Make sure the equalizer is available
            if (!equalizerManager.isAvailable()) {
                throw Exception("Equalizer not available")
            }
            
            // Get the number of bands
            val numberOfBands = equalizerManager.getNumberOfBands()
            if (numberOfBands <= 0) {
                throw Exception("No equalizer bands available")
            }
            
            Log.d("Equalizer", "Creating dialog with $numberOfBands bands")
            
            // Use the existing layout with predefined seekbars
        val seekBars = listOf(
            dialogView.findViewById<SeekBar>(R.id.band60Hz),
            dialogView.findViewById<SeekBar>(R.id.band230Hz),
            dialogView.findViewById<SeekBar>(R.id.band910Hz),
            dialogView.findViewById<SeekBar>(R.id.band3600Hz),
            dialogView.findViewById<SeekBar>(R.id.band14000Hz)
        )

            // Get the frequency labels
            val freqLabels = listOf(
                dialogView.findViewById<TextView>(R.id.text60Hz),
                dialogView.findViewById<TextView>(R.id.text230Hz),
                dialogView.findViewById<TextView>(R.id.text910Hz),
                dialogView.findViewById<TextView>(R.id.text3600Hz),
                dialogView.findViewById<TextView>(R.id.text14000Hz)
            )
            
            // Set up the SeekBars with the current equalizer settings
            val minLevel = equalizerManager.getMinBandLevel()
            val maxLevel = equalizerManager.getMaxBandLevel()
            
            // Get the minimum between the number of bands and the number of seekbars
            val bandsToShow = Math.min(numberOfBands, seekBars.size)
            
            for (i in 0 until bandsToShow) {
                val band = i.toShort()
                val currentLevel = equalizerManager.getBandLevel(band)
                val seekBar = seekBars[i]
                
                // Set up the SeekBar range and current value
                seekBar.max = maxLevel - minLevel
                seekBar.progress = currentLevel - minLevel
                
                // Update the frequency label with the actual center frequency
                val centerFreq = equalizerManager.getCenterFreq(band)
                val freqText = if (centerFreq >= 1000) {
                    String.format("%.1f kHz", centerFreq / 1000.0f)
                } else {
                    String.format("%d Hz", centerFreq.toInt())
                }
                freqLabels[i].text = freqText
                
                // Set up the SeekBar listener to update the equalizer
                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            val newLevel = (progress + minLevel).toShort()
                            try {
                                equalizerManager.setBandLevel(band, newLevel)
                                
                                // Also apply to the active music playback
                                val serviceIntent = Intent(this@SongDetailsActivity, MusicService::class.java).apply {
                                    action = "APPLY_EQUALIZER_SETTINGS"
                                    putExtra("bandLevels", equalizerManager.getBandLevels() as java.io.Serializable)
                                    putExtra("enabled", true)
                                }
                                startService(serviceIntent)
                            } catch (e: Exception) {
                                Log.e("Equalizer", "Error setting band level: ${e.message}")
                            }
                        }
                    }
                    
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            
            // Add a switch to enable/disable the equalizer
            val enableSwitch = dialogView.findViewById<Switch>(R.id.enableSwitch)
            enableSwitch.isChecked = equalizerManager.isEnabled()
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                try {
                    equalizerManager.setEnabled(isChecked)
                    
                    // Also apply to the active music playback
                    val serviceIntent = Intent(this@SongDetailsActivity, MusicService::class.java).apply {
                        action = "APPLY_EQUALIZER_SETTINGS"
                        putExtra("bandLevels", equalizerManager.getBandLevels() as java.io.Serializable)
                        putExtra("enabled", isChecked)
                    }
                    startService(serviceIntent)
                    
                    // Enable/disable all SeekBars
                    seekBars.forEach { it.isEnabled = isChecked }
                } catch (e: Exception) {
                    Log.e("Equalizer", "Error setting equalizer enabled: ${e.message}")
                }
            }
            
            // Add a reset button
            val resetButton = dialogView.findViewById<Button>(R.id.resetButton)
            resetButton.setOnClickListener {
                try {
                    // Get the minimum between the number of bands and the number of seekbars
                    val bandsToReset = Math.min(numberOfBands, seekBars.size)
                    
                    // Reset all bands to zero
                    for (i in 0 until bandsToReset) {
                        val band = i.toShort()
                        equalizerManager.setBandLevel(band, 0)
                        seekBars[i].progress = 0 - minLevel
                    }
                    
                    // Also apply to the active music playback
                    val serviceIntent = Intent(this@SongDetailsActivity, MusicService::class.java).apply {
                        action = "APPLY_EQUALIZER_SETTINGS"
                        putExtra("bandLevels", equalizerManager.getBandLevels() as java.io.Serializable)
                        putExtra("enabled", enableSwitch.isChecked)
                    }
                    startService(serviceIntent)
                    
                    Toast.makeText(this@SongDetailsActivity, "Equalizer reset", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("Equalizer", "Error resetting equalizer: ${e.message}")
                }
            }
            
            // Add buttons to the dialog
            dialogBuilder.setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            
            // Create and show the dialog
            equalizerDialog = dialogBuilder.create()
            equalizerDialog?.setOnDismissListener {
                // Release the temp MediaPlayer if it exists
                equalizerTempMediaPlayer?.release()
                equalizerTempMediaPlayer = null
                
                // Apply final settings to the active music playback
                val serviceIntent = Intent(this@SongDetailsActivity, MusicService::class.java).apply {
                    action = "APPLY_EQUALIZER_SETTINGS"
                    putExtra("bandLevels", equalizerManager.getBandLevels() as java.io.Serializable)
                    putExtra("enabled", enableSwitch.isChecked)
                }
                startService(serviceIntent)
            }
            equalizerDialog?.show()
            
        } catch (e: Exception) {
            Log.e("Equalizer", "Error creating equalizer dialog: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this@SongDetailsActivity, "Error creating equalizer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlaybackControls() {
        // Update play/pause button
        playPauseButtonDetails.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        
        // Start or stop the seekbar updates based on playback state
        if (isPlaying) {
            // Start seekbar updates if not already running
            updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable) // Remove any existing callbacks first
            updateSeekBarHandler.post(updateSeekBarRunnable) // Start updating the seekbar
            Log.d("SongDetailsActivity", "Started seekbar updates")
        } else {
            // Stop seekbar updates if playback is paused
            updateSeekBarHandler.removeCallbacks(updateSeekBarRunnable)
            Log.d("SongDetailsActivity", "Stopped seekbar updates")
        }
        
        // Update shuffle button
        updateShuffleButtonState()
        
        // Update repeat button
        val repeatIcon = when (repeatMode) {
            0 -> R.drawable.ic_repeat
            1 -> R.drawable.ic_repeat_on
            2 -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat
        }
        repeatButtonDetails.setImageResource(repeatIcon)
        repeatButtonDetails.setColorFilter(
            if (repeatMode != 0) ContextCompat.getColor(this@SongDetailsActivity, R.color.accent_color)
            else ContextCompat.getColor(this@SongDetailsActivity, R.color.white)
        )
    }

    private fun updateShuffleButtonState() {
        shuffleButtonDetails.setImageResource(if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
        val color = if (isShuffleEnabled) {
            ContextCompat.getColor(this@SongDetailsActivity, R.color.accent_color)
            } else {
            ContextCompat.getColor(this@SongDetailsActivity, R.color.white)
        }
        shuffleButtonDetails.clearColorFilter()
        shuffleButtonDetails.setColorFilter(color)
        shuffleButtonDetails.invalidate()
        Log.d("ShuffleControl", "Updated shuffle button: isEnabled=$isShuffleEnabled, color=${if (isShuffleEnabled) "accent" else "white"}")
    }

    private fun updateVolumeButtonState() {
        volumeButton.setImageResource(if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        val color = if (isMuted) {
            ContextCompat.getColor(this@SongDetailsActivity, R.color.accent_color)
        } else {
            ContextCompat.getColor(this@SongDetailsActivity, R.color.white)
        }
        volumeButton.clearColorFilter()
        volumeButton.setColorFilter(color)
        volumeButton.invalidate()
        Log.d("VolumeControl", "Updated volume button: isMuted=$isMuted, color=${if (isMuted) "accent" else "white"}")
    }

    private fun requestAudioSessionId() {
        try {
            val serviceIntent = Intent(this, MusicService::class.java).apply {
                action = "GET_AUDIO_SESSION_ID"
            }
            startService(serviceIntent)
            Log.d("Equalizer", "Requesting audio session ID, attempt: $equalizerSetupAttempts")
        } catch (e: Exception) {
            Log.e("Equalizer", "Error requesting audio session ID: ${e.message}")
        }
    }

    private fun registerReceivers() {
        // Register playback state receiver
        val playbackStateFilter = IntentFilter().apply {
            addAction("com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackStateReceiver, playbackStateFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackStateReceiver, playbackStateFilter)
        }

        // Register playback error receiver
        val errorFilter = IntentFilter("com.cap.ultimatemusicplayer.PLAYBACK_ERROR")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackErrorReceiver, errorFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackErrorReceiver, errorFilter)
        }

        // Register song change receiver
        val songChangeFilter = IntentFilter("com.cap.ultimatemusicplayer.SONG_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(songChangeReceiver, songChangeFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(songChangeReceiver, songChangeFilter)
        }

        // Register favorite status receiver
        val favoriteFilter = IntentFilter("com.cap.ultimatemusicplayer.FAVORITE_STATUS_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(favoriteStatusReceiver, favoriteFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(favoriteStatusReceiver, favoriteFilter)
        }

        // Register audio session receiver
        val audioSessionFilter = IntentFilter("com.cap.ultimatemusicplayer.AUDIO_SESSION_ID")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioSessionReceiver, audioSessionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(audioSessionReceiver, audioSessionFilter)
        }
        
        // Register seekbar update receiver
        val seekbarUpdateFilter = IntentFilter("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(playbackStateReceiver, seekbarUpdateFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(playbackStateReceiver, seekbarUpdateFilter)
        }
    }

    private fun requestPlaybackState() {
        // Request current playback state from service
        val serviceIntent = Intent(this, MusicService::class.java).apply {
            action = "GET_PLAYBACK_STATE"
        }
        startService(serviceIntent)
    }

    private fun onBindingComplete() {
        if (isBound && isFromPlaylist) {
            Log.d("SongDetailsActivity", "Service bound and song is from playlist. Ensuring playlist context is set.")
            // Ensure the MusicService has the playlist context
            val playlistManager = PlaylistManager(this)
            val playlist = playlistManager.getPlaylistById(playlistId)
            
            if (playlist != null && currentSongId != -1L) {
                val songIndex = playlist.songs.indexOfFirst { song -> song.id == currentSongId }
                if (songIndex != -1) {
                    val song = playlist.songs[songIndex]
                    musicService.playSong(song, playlist.songs, songIndex)
                    Log.d("SongDetailsActivity", "Set playlist context with ${playlist.songs.size} songs, current at index $songIndex")
                }
            }
        }
    }
}