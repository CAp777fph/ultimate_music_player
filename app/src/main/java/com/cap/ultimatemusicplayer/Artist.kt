package com.cap.ultimatemusicplayer

data class Artist(
    val name: String,
    var numberOfSongs: Int,
    var numberOfAlbums: Int,
    val songs: MutableList<Song>,
    val albums: MutableList<Album>,
    val imageUri: String? = null
) 