package com.cap.ultimatemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ArtistsFragment : Fragment() {
    private lateinit var artistsList: RecyclerView
    private lateinit var artistsAdapter: ArtistsAdapter
    private var artists = mutableListOf<Artist>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_artists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        artistsList = view.findViewById(R.id.artistsList)
        setupRecyclerView()
        
        // Get artists from MainActivity
        (activity as? MainActivity)?.let { mainActivity ->
            artists = mainActivity.getArtists()
            artistsAdapter.updateArtists(artists)
        }
    }

    private fun setupRecyclerView() {
        artistsAdapter = ArtistsAdapter(
            artists = artists,
            onArtistClick = { artist ->
                (activity as? MainActivity)?.let { mainActivity ->
                    // Show songs from this artist
                    mainActivity.showArtistSongs(artist.songs)
                    Toast.makeText(context, "${artist.name} এর গানগুলি দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
                }
            }
        )
        
        artistsList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = artistsAdapter
            setPadding(
                paddingLeft,
                paddingTop,
                paddingRight,
                resources.getDimensionPixelSize(R.dimen.bottom_playback_height)
            )
            clipToPadding = false
        }
    }

    fun updateArtists(newArtists: List<Artist>) {
        artists.clear()
        artists.addAll(newArtists)
        artistsAdapter.updateArtists(artists)
    }
} 