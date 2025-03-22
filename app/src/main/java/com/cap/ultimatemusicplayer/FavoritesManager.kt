package com.cap.ultimatemusicplayer

import android.content.Context
import android.content.SharedPreferences

class FavoritesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
    
    fun addFavorite(songId: Long) {
        prefs.edit().putBoolean(songId.toString(), true).apply()
    }
    
    fun removeFavorite(songId: Long) {
        prefs.edit().remove(songId.toString()).apply()
    }
    
    fun isFavorite(songId: Long): Boolean {
        return prefs.getBoolean(songId.toString(), false)
    }
    
    fun getAllFavorites(): Set<Long> {
        return prefs.all.mapNotNull { entry ->
            entry.key.toLongOrNull()?.takeIf { entry.value as? Boolean == true }
        }.toSet()
    }
    
    fun clearAllFavorites() {
        prefs.edit().clear().apply()
    }
} 