package com.cap.ultimatemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AlbumsFragment : Fragment() {
    private lateinit var albumsList: RecyclerView
    private lateinit var albumsAdapter: AlbumsAdapter
    private var albums = mutableListOf<Album>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_albums, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        albumsList = view.findViewById(R.id.albumsList)
        setupRecyclerView()
        
        // Get albums from MainActivity
        (activity as? MainActivity)?.let { mainActivity ->
            albums = mainActivity.getAlbums()
            albumsAdapter.updateAlbums(albums)
        }
    }

    private fun setupRecyclerView() {
        albumsAdapter = AlbumsAdapter(
            albums = albums,
            onAlbumClick = { album ->
                (activity as? MainActivity)?.let { mainActivity ->
                    // Show songs from this album
                    val albumSongs = mainActivity.getSongs().filter { it.album == album.name }
                    if (albumSongs.isNotEmpty()) {
                        mainActivity.showAlbumSongs(albumSongs)
                        Toast.makeText(context, "${album.name} এর গানগুলি দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "এই অ্যালবামে কোন গান নেই", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        
        albumsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = albumsAdapter
            setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                resources.getDimensionPixelSize(R.dimen.bottom_playback_height)
            )
            clipToPadding = false
        }
    }

    fun updateAlbums(newAlbums: List<Album>) {
        albums = newAlbums.toMutableList()
        albumsAdapter.updateAlbums(albums)
    }
}