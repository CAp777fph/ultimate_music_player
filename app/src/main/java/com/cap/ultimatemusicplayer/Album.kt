package com.cap.ultimatemusicplayer

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val albumArtUri: String,
    val numberOfSongs: Int,
    val songs: MutableList<Song>
) 