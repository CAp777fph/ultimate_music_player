package com.cap.ultimatemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.bumptech.glide.Glide

class ArtistsAdapter(
    private var artists: List<Artist> = emptyList(),
    private val onArtistClick: (Artist) -> Unit
) : RecyclerView.Adapter<ArtistsAdapter.ArtistViewHolder>() {

    class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val artistImage: ShapeableImageView = view.findViewById(R.id.artistImage)
        val artistName: TextView = view.findViewById(R.id.artistName)
        val artistDetails: TextView = view.findViewById(R.id.artistDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArtistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_artist, parent, false)
        return ArtistViewHolder(view)
    }

    override fun onBindViewHolder(holder: ArtistViewHolder, position: Int) {
        val artist = artists[position]
        
        holder.artistName.text = artist.name
        holder.artistDetails.text = "${artist.numberOfSongs} songs • ${artist.numberOfAlbums} albums"

        // Load artist image
        Glide.with(holder.artistImage)
            .load(artist.imageUri ?: R.drawable.ic_artist_placeholder)
            .placeholder(R.drawable.ic_artist_placeholder)
            .error(R.drawable.ic_artist_placeholder)
            .circleCrop()
            .into(holder.artistImage)

        holder.itemView.setOnClickListener { onArtistClick(artist) }
    }

    override fun getItemCount() = artists.size

    fun updateArtists(newArtists: List<Artist>) {
        artists = newArtists
        notifyDataSetChanged()
    }
} 