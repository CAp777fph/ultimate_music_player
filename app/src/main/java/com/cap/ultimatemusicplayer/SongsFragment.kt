package com.cap.ultimatemusicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast

class SongsFragment : Fragment() {
    private lateinit var songsList: RecyclerView
    private lateinit var songsAdapter: SongsAdapter
    private var songs = mutableListOf<Song>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        songsList = view.findViewById(R.id.songsList)
        setupRecyclerView()
        
        // Get songs from MainActivity
        (activity as? MainActivity)?.let { mainActivity ->
            songs = mainActivity.getSongs()
            songsAdapter.updateSongs(songs)
        }
    }

    private fun setupRecyclerView() {
        songsAdapter = SongsAdapter(
            songs = songs,
            onSongClick = { song ->
                (activity as? MainActivity)?.let { mainActivity ->
                    // Get both lists of songs
                    val mainActivitySongs = mainActivity.getSongs()
                    
                    // Try to find the song by ID in the MainActivity's list
                    val mainActivityPosition = mainActivitySongs.indexOfFirst { it.id == song.id }
                    
                    if (mainActivityPosition >= 0) {
                        // Found the song in MainActivity's list
                        mainActivity.playSong(mainActivityPosition)
                    } else {
                        // Fallback: If the song isn't in MainActivity's current list,
                        // we need to update MainActivity's song list first
                        mainActivity.showAllSongs()
                        // Now find the position again
                        val newPosition = mainActivity.getSongs().indexOfFirst { it.id == song.id }
                        if (newPosition >= 0) {
                            mainActivity.playSong(newPosition)
                        } else {
                            // Still can't find it - this is unusual
                            Toast.makeText(context, "Unable to play song: ${song.title}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Start SongDetailsActivity
                    val intent = Intent(context, SongDetailsActivity::class.java).apply {
                        putExtra("song_id", song.id)
                        putExtra("song_title", song.title)
                        putExtra("song_artist", song.artist)
                        putExtra("album_art_uri", song.albumArtUri)
                        putExtra("is_playing", true)
                        putExtra("is_shuffle_enabled", mainActivity.isShuffleEnabled)
                        putExtra("repeat_mode", mainActivity.repeatMode.ordinal)
                        putExtra("is_favorite", song.isFavorite)
                    }
                    startActivity(intent)
                }
            },
            onSongLongClick = { song ->
                (activity as? MainActivity)?.let { mainActivity ->
                    mainActivity.handleSongLongClick(song)
                }
                true
            }
        )
        
        songsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = songsAdapter
            setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                resources.getDimensionPixelSize(R.dimen.bottom_playback_height)
            )
            clipToPadding = false
        }
    }

    fun updateSongs(newSongs: List<Song>) {
        songs = newSongs.toMutableList()
        songsAdapter.updateSongs(songs)
        
        // Update the adapter's click listener to use the correct song list
        songsAdapter = SongsAdapter(
            songs = songs,
            onSongClick = { song ->
                (activity as? MainActivity)?.let { mainActivity ->
                    // Get both lists of songs
                    val mainActivitySongs = mainActivity.getSongs()
                    
                    // Try to find the song by ID in the MainActivity's list
                    val mainActivityPosition = mainActivitySongs.indexOfFirst { it.id == song.id }
                    
                    if (mainActivityPosition >= 0) {
                        // Found the song in MainActivity's list
                        mainActivity.playSong(mainActivityPosition)
                    } else {
                        // Fallback: If the song isn't in MainActivity's current list,
                        // we need to update MainActivity's song list first
                        mainActivity.showAllSongs()
                        // Now find the position again
                        val newPosition = mainActivity.getSongs().indexOfFirst { it.id == song.id }
                        if (newPosition >= 0) {
                            mainActivity.playSong(newPosition)
                        } else {
                            // Still can't find it - this is unusual
                            Toast.makeText(context, "Unable to play song: ${song.title}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    // Start SongDetailsActivity
                    val intent = Intent(context, SongDetailsActivity::class.java).apply {
                        putExtra("song_id", song.id)
                        putExtra("song_title", song.title)
                        putExtra("song_artist", song.artist)
                        putExtra("album_art_uri", song.albumArtUri)
                        putExtra("is_playing", true)
                        putExtra("is_shuffle_enabled", mainActivity.isShuffleEnabled)
                        putExtra("repeat_mode", mainActivity.repeatMode.ordinal)
                        putExtra("is_favorite", song.isFavorite)
                    }
                    startActivity(intent)
                }
            },
            onSongLongClick = { song ->
                (activity as? MainActivity)?.let { mainActivity ->
                    mainActivity.handleSongLongClick(song)
                }
                true
            }
        )
        
        songsList.adapter = songsAdapter
    }
}