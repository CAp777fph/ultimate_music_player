package com.cap.ultimatemusicplayer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var cachedPlaylists: List<Playlist>? = null

    fun getAllPlaylists(forceRefresh: Boolean = false): List<Playlist> {
        // If we have a cached version and don't need to force refresh, return it
        if (!forceRefresh && cachedPlaylists != null) {
            return cachedPlaylists!!
        }
        
        val playlistsJson = sharedPreferences.getString("playlists", null)
        val result = if (playlistsJson != null) {
            val type = object : TypeToken<List<Playlist>>() {}.type
            gson.fromJson<List<Playlist>>(playlistsJson, type)
        } else {
            emptyList()
        }
        
        // Update cache
        cachedPlaylists = result
        return result
    }

    fun addPlaylist(playlist: Playlist) {
        val playlists = getAllPlaylists().toMutableList()
        playlists.add(playlist)
        savePlaylists(playlists)
    }

    fun removePlaylist(playlistId: Long) {
        val playlists = getAllPlaylists().toMutableList()
        playlists.removeAll { it.id == playlistId }
        savePlaylists(playlists)
    }

    fun deletePlaylists(playlistIds: List<Long>) {
        val playlists = getAllPlaylists().toMutableList()
        playlists.removeAll { it.id in playlistIds }
        savePlaylists(playlists)
    }

    fun updatePlaylist(playlist: Playlist) {
        val playlists = getAllPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            playlists[index] = playlist
            savePlaylists(playlists)
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        val playlists = getAllPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = playlists[index]
            val updatedSongs = playlist.songs.toMutableList()
            if (!updatedSongs.contains(song)) {
                updatedSongs.add(song)
                playlists[index] = playlist.copy(songs = updatedSongs)
                savePlaylists(playlists)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        val playlists = getAllPlaylists().toMutableList()
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = playlists[index]
            val updatedSongs = playlist.songs.filterNot { it.id == songId }
            playlists[index] = playlist.copy(songs = updatedSongs)
            savePlaylists(playlists)
        }
    }

    /**
     * Get a playlist by its ID
     * @param playlistId The ID of the playlist to retrieve
     * @return The playlist with the given ID, or null if not found
     */
    fun getPlaylistById(playlistId: Long): Playlist? {
        return getAllPlaylists().find { it.id == playlistId }
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        val editor = sharedPreferences.edit()
        val playlistsJson = gson.toJson(playlists)
        editor.putString("playlists", playlistsJson)
        
        // Apply changes immediately for faster updates
        editor.commit() // Using commit instead of apply for immediate disk write
        
        // Update cache after saving
        cachedPlaylists = playlists
    }
} 