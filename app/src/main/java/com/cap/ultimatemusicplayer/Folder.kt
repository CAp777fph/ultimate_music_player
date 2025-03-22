package com.cap.ultimatemusicplayer

data class Folder(
    val name: String,
    val path: String,
    val numberOfSongs: Int,
    val songs: List<Song>
) 