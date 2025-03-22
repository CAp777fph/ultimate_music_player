package com.cap.ultimatemusicplayer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAllPlaylists(): List<Playlist> {
        val playlistsJson = sharedPreferences.getString("playlists", null)
        return if (playlistsJson != null) {
            val type = object : TypeToken<List<Playlist>>() {}.type
            gson.fromJson(playlistsJson, type)
        } else {
            emptyList()
        }
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

    private fun savePlaylists(playlists: List<Playlist>) {
        val playlistsJson = gson.toJson(playlists)
        sharedPreferences.edit().putString("playlists", playlistsJson).apply()
    }
} 