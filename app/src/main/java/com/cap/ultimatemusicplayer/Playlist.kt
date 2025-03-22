package com.cap.ultimatemusicplayer

data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<Song>
) {
    // Computed property for song count
    val songCount: Int
        get() = songs.size
} 