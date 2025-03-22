package com.cap.ultimatemusicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.io.File
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FoldersFragment : Fragment() {
    private lateinit var foldersList: RecyclerView
    private lateinit var foldersAdapter: FoldersAdapter
    private var folders = mutableListOf<Folder>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        foldersList = view.findViewById(R.id.foldersList)
        setupRecyclerView()
        loadFolders()
    }

    private fun setupRecyclerView() {
        foldersAdapter = FoldersAdapter(
            folders = folders,
            onFolderClick = { folder ->
                // Handle folder click - show songs in this folder
                (activity as? MainActivity)?.showFolderSongs(folder)
            }
        )
        foldersList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = foldersAdapter
            setPadding(paddingLeft, paddingTop, paddingRight, resources.getDimensionPixelSize(R.dimen.bottom_playback_height))
            clipToPadding = false
        }
    }

    fun updateFolders(newFolders: List<Folder>) {
        folders.clear()
        folders.addAll(newFolders)
        foldersAdapter.updateFolders(folders)
    }

    private fun loadFolders() {
        val folders = mutableListOf<Folder>()
        val audioFolders = mutableMapOf<String, MutableList<Song>>()

        // Get all songs from MainActivity
        val songs = (activity as? MainActivity)?.getSongs() ?: return

        // Group songs by their parent folder
        songs.forEach { song ->
            val folderPath = File(song.path).parent ?: return@forEach
            val folderSongs = audioFolders.getOrPut(folderPath) { mutableListOf() }
            folderSongs.add(song)
        }

        // Create Folder objects
        audioFolders.forEach { (path, songs) ->
            val folder = File(path)
            folders.add(
                Folder(
                    name = folder.name,
                    path = path,
                    numberOfSongs = songs.size,
                    songs = songs
                )
            )
        }

        // Sort folders by name
        folders.sortBy { it.name }
        updateFolders(folders)
    }
} 