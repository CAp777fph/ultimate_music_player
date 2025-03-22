package com.cap.ultimatemusicplayer

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import java.io.File

class MusicService : Service() {
    private var mediaPlayer: android.media.MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private val binder = MusicBinder()
    private var currentSong: Song? = null
    private var _isPlaying = false
    private var _isShuffleEnabled = false
    private var repeatMode = MainActivity.RepeatMode.NONE
    private var _isMuted = false
    private var _currentSpeed = 1.0f
    private val handler = Handler(Looper.getMainLooper())
    private var equalizer: Equalizer? = null
    private var equalizerEnabled = false
    private var exoPlayer: SimpleExoPlayer? = null
    
    // Playlist tracking
    private var playlistSongs: List<Song>? = null
    private var currentPlaylistPosition: Int = -1
    private var isPlaylistActive: Boolean = false

    private val updateSeekBarRunnable = object : Runnable {
        override fun run() {
            if (_isPlaying) {
                exoPlayer?.let { player ->
                    val currentPosition = player.currentPosition.toInt()
                    val duration = player.duration.toInt()
                    
                    Log.d("MusicService", "Seekbar update: position=$currentPosition, duration=$duration")

                    // Always send both UI and notification updates
                    sendBroadcast(Intent("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE").apply {
                        putExtra("current_position", currentPosition)
                        putExtra("duration", duration)
                        // Add flags to ensure high priority delivery
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    })

                    // Also include position in playback state update for consistency
                    updateMediaSessionPlaybackState(currentPosition.toLong())
                    
                    // If current song info is available, consider re-sending it to ensure UI is in sync
                    currentSong?.let { song ->
                        if (Math.random() < 0.05) { // Occasionally refresh song info (5% chance each update)
                            broadcastSongChange(song)
                        }
                    }
                }
                // Update more frequently for smoother seekbar movement
                handler.postDelayed(this, 500) // Update twice per second
            }
        }
    }
    private var _isMediaPlayerReady = false

    // Public getters for MediaPlayer properties
    val playerPosition: Int
        get() = try {
            exoPlayer?.currentPosition?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }

    val duration: Int
        get() = try {
            exoPlayer?.duration?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }

    // Public getter for isPlaying
    val isPlaying: Boolean
        get() = _isPlaying

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, "UltimateMusicPlayer").apply {
            // Set flags to handle media buttons and transport controls
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

            // Set callback to handle media button events
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    togglePlayPause()
                }

                override fun onPause() {
                    togglePlayPause()
                }

                override fun onSkipToNext() {
                    playNextInPlaylist()
                }

                override fun onSkipToPrevious() {
                    playPreviousInPlaylist()
                }

                override fun onSeekTo(pos: Long) {
                    exoPlayer?.seekTo(pos)
                }
            })

            // Activate the session
            isActive = true
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If service is restarted after being killed, restore the notification
        if (intent == null && flags == Service.START_FLAG_REDELIVERY) {
            Log.d("MusicService", "Service restarted after being killed, restoring state")
            if (currentSong != null && exoPlayer != null) {
                updateNotification()
                sendPlaybackStateUpdate()
            }
            return START_STICKY
        }

        when (intent?.action) {
            "ACTION_PLAY_PAUSE" -> togglePlayPause()
            "ACTION_NEXT" -> playNextInPlaylist()
            "ACTION_PREVIOUS" -> playPreviousInPlaylist()
            "GET_AUDIO_SESSION_ID" -> {
                // Always send the current audio session ID, even if ExoPlayer isn't fully ready
                exoPlayer?.let { player ->
                    val audioSessionId = player.audioSessionId
                    Log.d("Equalizer", "Current ExoPlayer state: ready=${_isMediaPlayerReady}, sessionId=$audioSessionId")

                    if (audioSessionId != 0) {
                        Log.d("Equalizer", "Sending valid audio session ID: $audioSessionId")
                        sendBroadcast(Intent("com.cap.ultimatemusicplayer.AUDIO_SESSION_ID").apply {
                            putExtra("audioSessionId", audioSessionId)
                            putExtra("isMediaPlayerReady", true)
                        })
                    } else {
                        Log.e("Equalizer", "Invalid audio session ID (0)")
                        createTempMediaPlayerForEqualizer()
                    }
                } ?: run {
                    Log.e("Equalizer", "ExoPlayer is null, creating temporary one")
                    createTempMediaPlayerForEqualizer()
                }
            }
            "GET_PLAYBACK_STATE" -> {
                // Send current playback state
                sendPlaybackStateUpdate()

                // Send current seekbar position
                exoPlayer?.let { player ->
                    sendBroadcast(Intent("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE").apply {
                        putExtra("current_position", player.currentPosition)
                        putExtra("duration", player.duration)
                    })
                }
            }
            "GET_CURRENT_POSITION" -> {
                // Send current seekbar position in response to direct request
                exoPlayer?.let { player ->
                    val currentPosition = player.currentPosition.toInt()
                    val duration = player.duration.toInt()
                    Log.d("MusicService", "Responding to GET_CURRENT_POSITION with pos=$currentPosition, duration=$duration")
                    sendBroadcast(Intent("com.cap.ultimatemusicplayer.SEEKBAR_UPDATE").apply {
                        putExtra("current_position", currentPosition)
                        putExtra("duration", duration)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    })
                }
            }
            "APPLY_EQUALIZER_SETTINGS" -> {
                val bandLevels = intent.getSerializableExtra("bandLevels") as? HashMap<Short, Short>
                val enabled = intent.getBooleanExtra("enabled", false)

                Log.d("Equalizer", "Received equalizer settings: enabled=$enabled, bands=${bandLevels?.size}")

                if (bandLevels != null) {
                    applyEqualizerSettings(bandLevels, enabled)
                }
            }
            "UPDATE_SONG_TITLE" -> {
                val songId = intent.getLongExtra("song_id", -1)
                val newTitle = intent.getStringExtra("new_title") ?: ""

                updateSongIfActive(songId, newTitle)
            }
        }
        return START_STICKY
    }

    private fun createTempMediaPlayerForEqualizer() {
        try {
            // Create a temporary MediaPlayer just to get a valid session ID
            val tempPlayer = android.media.MediaPlayer()

            // Try to set a dummy audio source
            try {
                val assetFileDescriptor = assets.openFd("dummy_silence.mp3")
                tempPlayer.setDataSource(
                    assetFileDescriptor.fileDescriptor,
                    assetFileDescriptor.startOffset,
                    assetFileDescriptor.length
                )
                tempPlayer.prepare()
                Log.d("Equalizer", "Temp MediaPlayer prepared successfully")
            } catch (e: Exception) {
                Log.e("Equalizer", "Error preparing temp MediaPlayer: ${e.message}")
                // Continue anyway, we might still get a valid session ID
            }

            val sessionId = tempPlayer.audioSessionId
            Log.d("Equalizer", "Created temp MediaPlayer with session ID: $sessionId")

            if (sessionId != 0) {
                // Send the session ID
                sendBroadcast(Intent("com.cap.ultimatemusicplayer.AUDIO_SESSION_ID").apply {
                    putExtra("audioSessionId", sessionId)
                    putExtra("isMediaPlayerReady", true)
                    putExtra("isTemporary", true)
                })

                // We don't need to keep this player around
                tempPlayer.release()
            } else {
                sendBroadcast(Intent("com.cap.ultimatemusicplayer.AUDIO_SESSION_ID").apply {
                    putExtra("audioSessionId", 0)
                    putExtra("isMediaPlayerReady", false)
                    putExtra("error", "Could not create valid audio session ID")
                })
                tempPlayer.release()
            }
        } catch (e: Exception) {
            Log.e("Equalizer", "Error creating temp MediaPlayer: ${e.message}")
            sendBroadcast(Intent("com.cap.ultimatemusicplayer.AUDIO_SESSION_ID").apply {
                putExtra("audioSessionId", 0)
                putExtra("isMediaPlayerReady", false)
                putExtra("error", "Error: ${e.message}")
            })
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun playSong(song: Song) {
        Log.d("MusicService", "Starting to play song: ${song.title} with path: ${song.path}")

        // Always clean up existing players first
        cleanupMediaPlayer()

        try {
            // Create a new ExoPlayer instance
            exoPlayer = SimpleExoPlayer.Builder(applicationContext).build()

            // Create a media item
            val mediaItem = if (song.path.startsWith("content://")) {
                // For content URIs
                MediaItem.fromUri(Uri.parse(song.path))
            } else {
                // For file paths
                val file = File(song.path)
                if (!file.exists()) {
                    Log.e("MusicService", "Song file does not exist: ${song.path}")
                    return
                }
                MediaItem.fromUri(Uri.fromFile(file))
            }

            // Set the media item to be played
            exoPlayer?.setMediaItem(mediaItem)

            // Prepare the player
            exoPlayer?.prepare()

            // Start playback
            exoPlayer?.play()

            // Set listeners
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            _isPlaying = true
                            _isMediaPlayerReady = true

                            // Start the seekbar update immediately
                            handler.removeCallbacks(updateSeekBarRunnable)
                            handler.post(updateSeekBarRunnable)
                            
                            // Update UI
                            updateMediaSessionMetadata()
                            sendPlaybackStateUpdate()
                            updateNotification()

                            // Broadcast song change immediately after ready state
                            broadcastSongChange(song)

                            Log.d("MusicService", "ExoPlayer playback started successfully")
                        }
                        Player.STATE_ENDED -> {
                            _isPlaying = false
                            _isMediaPlayerReady = false
                            handler.removeCallbacks(updateSeekBarRunnable)
                            sendPlaybackStateUpdate()
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d("MusicService", "ExoPlayer buffering")
                        }
                        Player.STATE_IDLE -> {
                            Log.d("MusicService", "ExoPlayer idle")
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("MusicService", "ExoPlayer error: ${error.message}", error)
                    _isPlaying = false
                    _isMediaPlayerReady = false
                    handler.removeCallbacks(updateSeekBarRunnable)
                    sendPlaybackStateUpdate()
                }
            })

            // Update current song
            currentSong = song
            
            // Send initial change broadcast immediately
            broadcastSongChange(song)

        } catch (e: Exception) {
            Log.e("MusicService", "Fatal error in playSong", e)
            cleanupMediaPlayer()
        }
    }
    
    /**
     * Helper method to broadcast song change events consistently
     */
    private fun broadcastSongChange(song: Song) {
        // Broadcast song change with all necessary details
        val intent = Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
            putExtra("song_id", song.id)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_album", song.album)
            putExtra("song_album_art_uri", song.albumArtUri)
            putExtra("album_art_uri", song.albumArtUri) // Include both key formats for compatibility
            putExtra("is_playing", _isPlaying)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
        Log.d("MusicService", "Broadcasted song change: ${song.title} with album art URI: ${song.albumArtUri}")
    }
    
    /**
     * Play a song from a playlist with context of all songs in the playlist
     */
    fun playSong(song: Song, playlistSongs: List<Song>, position: Int) {
        // Log details for debugging
        Log.d("MusicService", "Playing song from playlist: ${song.title} at position $position")
        Log.d("MusicService", "Album art URI: ${song.albumArtUri}")
        
        // Save the current playlist context
        this.playlistSongs = playlistSongs
        this.currentPlaylistPosition = position
        this.isPlaylistActive = true
        
        // Play the selected song - this will trigger the regular playSong method
        playSong(song)
        
        // Notify about playlist context change
        val intent = Intent("com.cap.ultimatemusicplayer.SET_PLAYLIST").apply {
            putExtra("current_position", position)
            putExtra("playlist_active", true)
            putExtra("song_id", song.id)  // Add song ID for reference
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Play the next song in the current playlist context
     */
    fun playNextInPlaylist() {
        if (!isPlaylistActive || playlistSongs.isNullOrEmpty() || currentPlaylistPosition < 0) {
            // Fall back to regular next if no playlist context
            Log.d("MusicService", "No playlist context, falling back to regular next")
            playNext()
            return
        }
        
        Log.d("MusicService", "Playing next song in playlist. Current position: $currentPlaylistPosition, Total songs: ${playlistSongs?.size}")
        
        // Calculate next position
        val nextPosition = when {
            repeatMode == MainActivity.RepeatMode.ONE -> currentPlaylistPosition // Stay on current song
            _isShuffleEnabled -> {
                // Use shuffle logic here if needed
                val random = java.util.Random()
                random.nextInt(playlistSongs!!.size)
            }
            else -> {
                val nextPos = (currentPlaylistPosition + 1) % playlistSongs!!.size
                if (nextPos == 0 && repeatMode != MainActivity.RepeatMode.ALL) {
                    // End of playlist without repeat all
                    Log.d("MusicService", "End of playlist reached without repeat all")
                    stopPlayback()
                    return
                }
                nextPos
            }
        }
        
        // Play the next song
        currentPlaylistPosition = nextPosition
        val nextSong = playlistSongs!![nextPosition]
        Log.d("MusicService", "Playing next song: ${nextSong.title} with album art: ${nextSong.albumArtUri}")
        
        // Play the song (this will trigger all necessary broadcasts)
        playSong(nextSong)
        
        // Notify about playlist position change
        val intent = Intent("com.cap.ultimatemusicplayer.SET_PLAYLIST").apply {
            putExtra("current_position", nextPosition)
            putExtra("playlist_active", true)
            putExtra("song_id", nextSong.id)  // Include song ID for reference
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Play the previous song in the current playlist context
     */
    fun playPreviousInPlaylist() {
        if (!isPlaylistActive || playlistSongs.isNullOrEmpty() || currentPlaylistPosition < 0) {
            // Fall back to regular previous if no playlist context
            playPrevious()
            return
        }
        
        // Calculate previous position
        val prevPosition = when {
            repeatMode == MainActivity.RepeatMode.ONE -> currentPlaylistPosition // Stay on current song
            _isShuffleEnabled -> {
                // Use shuffle logic here if needed
                val random = java.util.Random()
                random.nextInt(playlistSongs!!.size)
            }
            else -> {
                if (currentPlaylistPosition > 0) {
                    currentPlaylistPosition - 1
                } else if (repeatMode == MainActivity.RepeatMode.ALL) {
                    playlistSongs!!.size - 1
                } else {
                    // Beginning of playlist without repeat
                    return
                }
            }
        }
        
        // Play the previous song
        currentPlaylistPosition = prevPosition
        val prevSong = playlistSongs!![prevPosition]
        playSong(prevSong)
        
        // Notify about playlist position change
        val intent = Intent("com.cap.ultimatemusicplayer.SET_PLAYLIST").apply {
            putExtra("current_position", prevPosition)
            putExtra("playlist_active", true)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        sendBroadcast(intent)
    }
    
    /**
     * Add a song to be played next in the queue
     */
    fun playNext(song: Song) {
        // Send broadcast to MainActivity to handle adding song to play next
        sendBroadcast(Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
            setPackage(packageName)
            putExtra("command", "PLAY_NEXT_SONG")
            putExtra("song_id", song.id)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_path", song.path)
            putExtra("song_duration", song.duration)
            putExtra("song_album", song.album)
            putExtra("song_album_art_uri", song.albumArtUri)
        })
    }
    
    /**
     * Add a song to the end of the queue
     */
    fun addToQueue(song: Song) {
        // Send broadcast to MainActivity to handle adding song to queue
        sendBroadcast(Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
            setPackage(packageName)
            putExtra("command", "ADD_TO_QUEUE")
            putExtra("song_id", song.id)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_path", song.path)
            putExtra("song_duration", song.duration)
            putExtra("song_album", song.album)
            putExtra("song_album_art_uri", song.albumArtUri)
        })
    }
    
    /**
     * Stop the current playback
     */
    private fun stopPlayback() {
        try {
            exoPlayer?.stop()
            _isPlaying = false
            sendPlaybackStateUpdate()
            updateNotification()
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e("MusicService", "Error stopping playback", e)
        }
    }

    // Update cleanupMediaPlayer to handle ExoPlayer
    private fun cleanupMediaPlayer() {
        try {
            // Clean up MediaPlayer
            mediaPlayer?.apply {
                if (isPlaying) {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.e("MusicService", "Error stopping MediaPlayer", e)
                    }
                }

                try {
                    release()
                } catch (e: Exception) {
                    Log.e("MusicService", "Error releasing MediaPlayer", e)
                }
            }

            // Clean up ExoPlayer
            exoPlayer?.apply {
                try {
                    stop()
                    release()
                } catch (e: Exception) {
                    Log.e("MusicService", "Error releasing ExoPlayer", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicService", "Error cleaning up players", e)
        } finally {
            mediaPlayer = null
            exoPlayer = null
            _isPlaying = false
            _isMediaPlayerReady = false
            handler.removeCallbacks(updateSeekBarRunnable)
        }
    }

    // Update other methods to use ExoPlayer
    fun togglePlayPause() {
        if (_isPlaying) {
            pauseSong()
        } else {
            resumeSong()
        }
    }

    fun pauseSong() {
        try {
            exoPlayer?.pause()
            _isPlaying = false
            sendPlaybackStateUpdate()
            updateNotification()
            handler.removeCallbacks(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e("MusicService", "Error pausing song", e)
        }
    }

    fun resumeSong() {
        try {
            exoPlayer?.play()
            _isPlaying = true
            sendPlaybackStateUpdate()
            updateNotification()
            handler.post(updateSeekBarRunnable)
        } catch (e: Exception) {
            Log.e("MusicService", "Error resuming song", e)
        }
    }

    fun seekTo(position: Int) {
        try {
            exoPlayer?.seekTo(position.toLong())
            sendPlaybackStateUpdate()
        } catch (e: Exception) {
            Log.e("MusicService", "Error seeking", e)
        }
    }

    fun playNext() {
        // Send broadcast to MainActivity to handle next song
        sendBroadcast(Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
            setPackage(packageName)
            putExtra("command", "PLAY_NEXT")
        })

        // Update MediaSession state
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                    exoPlayer?.currentPosition?.toLong() ?: 0,
                    _currentSpeed  // Use current speed instead of hardcoded 1.0f
                )
                .build()
        )
    }

    fun playPrevious() {
        // Send broadcast to MainActivity to handle previous song
        sendBroadcast(Intent("com.cap.ultimatemusicplayer.PLAYBACK_CONTROL").apply {
            setPackage(packageName)
            putExtra("command", "PLAY_PREVIOUS")
        })

        // Update MediaSession state
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                    exoPlayer?.currentPosition?.toLong() ?: 0,
                    _currentSpeed  // Use current speed instead of hardcoded 1.0f
                )
                .build()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current playing song"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val song = currentSong ?: return

        try {
            // Update media session metadata first
            updateMediaSessionMetadata()

            // Update media session playback state
            updateMediaSessionPlaybackState()

            // Create pending intents for notification actions
            val playPauseIntent = PendingIntent.getService(
                this,
                0,
                Intent(this, MusicService::class.java).apply { action = "ACTION_PLAY_PAUSE" },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, MusicService::class.java).apply { action = "ACTION_NEXT" },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val previousIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, MusicService::class.java).apply { action = "ACTION_PREVIOUS" },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create content intent to open SongDetailsActivity
            val contentIntent = PendingIntent.getActivity(
                this,
                3,
                Intent(this, SongDetailsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("song_id", song.id)
                    putExtra("song_title", song.title)
                    putExtra("song_artist", song.artist)
                    putExtra("album_art_uri", song.albumArtUri)
                    putExtra("is_playing", _isPlaying)
                    putExtra("is_shuffle_enabled", _isShuffleEnabled)
                    putExtra("repeat_mode", repeatMode.ordinal)
                    putExtra("playback_speed", _currentSpeed)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Load album art
            val albumArt = try {
                val uri = Uri.parse(song.albumArtUri)
                val inputStream = contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(inputStream)
            } catch (e: Exception) {
                BitmapFactory.decodeResource(resources, R.drawable.default_album_art2)
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setLargeIcon(albumArt)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setContentIntent(contentIntent)
                .setStyle(MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2))
                .addAction(R.drawable.ic_previous, "Previous", previousIntent)
                .addAction(
                    if (_isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (_isPlaying) "Pause" else "Play",
                    playPauseIntent
                )
                .addAction(R.drawable.ic_next, "Next", nextIntent)
                .setColorized(true)
                .setColor(ContextCompat.getColor(this, R.color.accent_color))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(_isPlaying)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e("MusicService", "Error updating notification: ${e.message}", e)
        }
    }

    private fun updateMediaSessionPlaybackState(position: Long = -1) {
        val state = if (_isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actualPosition = if (position > 0) position else {
            try {
                exoPlayer?.currentPosition ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
        
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    state,
                    actualPosition,
                    _currentSpeed
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
    }
    
    private fun updateMediaSessionMetadata() {
        currentSong?.let { song ->
            try {
                // Load album art
                val albumArt = try {
                    val uri = Uri.parse(song.albumArtUri)
                    val inputStream = contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } catch (e: Exception) {
                    BitmapFactory.decodeResource(resources, R.drawable.default_album_art2)
                }
                
                val duration = try {
                    exoPlayer?.duration?.toLong() ?: 0L
                } catch (e: IllegalStateException) {
                    0L
                }
                
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                
                mediaSession.setMetadata(metadataBuilder.build())
                Log.d("MusicService", "Updated media session metadata with duration: $duration")
            } catch (e: Exception) {
                Log.e("MusicService", "Error updating metadata: ${e.message}")
            }
        }
    }

    private fun sendPlaybackStateUpdate() {
        // Send broadcast to update UI state with all playback information
        val intent = Intent("com.cap.ultimatemusicplayer.PLAYBACK_STATE_UPDATE").apply {
            putExtra("is_playing", _isPlaying)
            putExtra("repeat_mode", repeatMode.ordinal)
            putExtra("playback_speed", _currentSpeed)
            putExtra("is_muted", _isMuted)
        }
        Log.d("VolumeControl", "Service sending mute state: $_isMuted")
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        exoPlayer?.release()
        exoPlayer = null
        mediaSession.release()
    }

    fun setPlaybackSpeed(speed: Float) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                exoPlayer?.let { player ->
                    try {
                        player.setPlaybackParameters(PlaybackParameters(speed))
                        _currentSpeed = speed
                        Log.d("SpeedControl", "Service setting speed to: $_currentSpeed")
                        
                        // Update MediaSession state with current speed
                        mediaSession.setPlaybackState(
                            PlaybackStateCompat.Builder()
                                .setState(
                                    if (_isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                                    player.currentPosition,
                                    _currentSpeed
                                )
                                .setActions(
                                    PlaybackStateCompat.ACTION_PLAY or
                                    PlaybackStateCompat.ACTION_PAUSE or
                                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                                )
                                .build()
                        )
                        
                        // Send broadcast with full state update including speed
                        sendPlaybackStateUpdate()
                    } catch (e: Exception) {
                        Log.e("SpeedControl", "Error setting playback speed: ${e.message}")
                    }
                } ?: run {
                    Log.e("SpeedControl", "ExoPlayer is null, can't set speed")
                }
            }
        } catch (e: Exception) {
            Log.e("SpeedControl", "Error in setPlaybackSpeed: ${e.message}")
        }
    }

    fun setVolume(leftVolume: Float, rightVolume: Float) {
        try {
            exoPlayer?.setVolume(leftVolume)
            _isMuted = (leftVolume == 0f && rightVolume == 0f)
            Log.d("VolumeControl", "Volume set to: ${if (_isMuted) "muted" else "unmuted"}")
            // Send broadcast with full state update including mute status
            sendPlaybackStateUpdate()
        } catch (e: Exception) {
            Log.e("MusicService", "Error setting volume: ${e.message}")
        }
    }

    fun setRepeatMode(mode: MainActivity.RepeatMode) {
        repeatMode = mode
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _isShuffleEnabled = enabled
    }

    fun getCurrentAudioSessionId(): Int {
        return exoPlayer?.audioSessionId ?: 0
    }

    /**
     * Returns the current song being played
     * Used by MainActivity to update UI
     */
    fun getCurrentSong(): Song? {
        return currentSong
    }

    private fun saveEqualizerSettings(): HashMap<Short, Short>? {
        if (equalizer == null) {
            Log.d("Equalizer", "No equalizer instance to save settings from")
            return null
        }
        
        val bandLevels = HashMap<Short, Short>()
        try {
            equalizer?.let { eq ->
                if (!eq.enabled) {
                    Log.d("Equalizer", "Equalizer is disabled, no settings to save")
                    return null
                }
                
                val numberOfBands = eq.numberOfBands
                if (numberOfBands <= 0) {
                    Log.d("Equalizer", "Equalizer has no bands, no settings to save")
                    return null
                }
                
                for (i in 0 until numberOfBands) {
                    try {
                        val band = i.toShort()
                        val level = eq.getBandLevel(band)
                        bandLevels[band] = level
                        Log.d("Equalizer", "Saved band $band level: $level")
                    } catch (e: Exception) {
                        Log.e("Equalizer", "Error saving band $i level: ${e.message}")
                        // Continue with other bands
                    }
                }
            }
            
            Log.d("Equalizer", "Successfully saved equalizer settings for ${bandLevels.size} bands")
            return if (bandLevels.isEmpty()) null else bandLevels
            
        } catch (e: Exception) {
            Log.e("Equalizer", "Error saving equalizer settings: ${e.message}", e)
            return null
        }
    }
    
    private fun applyEqualizerSettings(bandLevels: HashMap<Short, Short>, enabled: Boolean) {
        if (bandLevels.isEmpty()) {
            Log.d("Equalizer", "No band levels to apply, skipping equalizer setup")
            return
        }
        
        try {
            val player = exoPlayer ?: run {
                Log.e("Equalizer", "ExoPlayer is null, can't apply equalizer settings")
                return
            }
            
            // Release existing equalizer if any
            try {
                equalizer?.release()
            } catch (e: Exception) {
                Log.e("Equalizer", "Error releasing previous equalizer: ${e.message}")
            }
            equalizer = null
            
            // Create new equalizer for current session
            val sessionId = player.audioSessionId
            if (sessionId == 0) {
                Log.e("Equalizer", "Invalid audio session ID (0), can't apply equalizer settings")
                return
            }
            
            try {
                equalizer = Equalizer(0, sessionId)
                equalizer?.let { eq ->
                    // Set enabled state
                    eq.enabled = enabled
                    equalizerEnabled = enabled
                    
                    // Apply band levels
                    bandLevels.forEach { (band, level) ->
                        try {
                            if (band < eq.numberOfBands) {
                                eq.setBandLevel(band, level)
                                Log.d("Equalizer", "Applied band $band level $level to active ExoPlayer")
                            }
                        } catch (e: Exception) {
                            Log.e("Equalizer", "Error setting band $band level: ${e.message}")
                        }
                    }
                    
                    Log.d("Equalizer", "Successfully applied equalizer settings to active ExoPlayer")
                }
            } catch (e: Exception) {
                Log.e("Equalizer", "Error creating equalizer: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("Equalizer", "Error applying equalizer settings: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("MusicService", "Task removed, ensuring service continues running")
        
        // Save current state if needed
        if (currentSong != null && exoPlayer != null && _isPlaying) {
            // Ensure notification is showing to keep service alive
            updateNotification()
            
            // Request service restart if killed
            val restartServiceIntent = Intent(applicationContext, MusicService::class.java)
            val restartServicePendingIntent = PendingIntent.getService(
                applicationContext, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        }
    }

    /**
     * Updates the currently playing song's title if it matches the given ID
     * This is called when a song is renamed in the MainActivity
     */
    fun updateSongIfActive(songId: Long, newTitle: String) {
        // If the song being updated is the currently playing song, update it
        if (currentSong?.id == songId) {
            // Update the object property
            currentSong = currentSong?.copy(title = newTitle)
            
            // Update metadata
            updateMediaSessionMetadata()
            
            // Update notification
            updateNotification()
            
            // Broadcast song change to update UI in MainActivity
            val song = currentSong ?: return
            val intent = Intent("com.cap.ultimatemusicplayer.SONG_CHANGED").apply {
                putExtra("song_id", song.id)
                putExtra("song_title", song.title)
                putExtra("song_artist", song.artist)
                putExtra("song_album", song.album)
                putExtra("song_album_art_uri", song.albumArtUri)
                putExtra("album_art_uri", song.albumArtUri) // Include both key formats for compatibility
                putExtra("is_playing", _isPlaying)
            }
            sendBroadcast(intent)
            
            Log.d("MusicService", "Updated currently playing song title to: $newTitle")
        }
    }

    private fun createNotification(song: Song): Notification {
        val notificationIntent = Intent(this, SongDetailsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("song_id", song.id)
            putExtra("song_title", song.title)
            putExtra("song_artist", song.artist)
            putExtra("song_album", song.album)
            putExtra("song_album_art_uri", song.albumArtUri)
            putExtra("album_art_uri", song.albumArtUri) // Add consistent key
            putExtra("is_playing", _isPlaying)
            putExtra("is_shuffle_enabled", _isShuffleEnabled)
            putExtra("repeat_mode", repeatMode.ordinal)
            putExtra("playback_speed", _currentSpeed)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Load album art
        val albumArt = try {
            val uri = Uri.parse(song.albumArtUri)
            val inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(resources, R.drawable.default_album_art2)
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(albumArt)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(song.title)
                .setBigContentTitle(song.artist))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .build()
            
        return notification
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
    }
}