package com.cap.ultimatemusicplayer

import android.content.Context
import android.content.SharedPreferences

class PlayCountManager(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "play_counts",
        Context.MODE_PRIVATE
    )

    fun getPlayCount(songId: Long): Int {
        return sharedPreferences.getInt(songId.toString(), 0)
    }

    fun incrementPlayCount(songId: Long) {
        val currentCount = getPlayCount(songId)
        sharedPreferences.edit().putInt(songId.toString(), currentCount + 1).apply()
    }

    fun setPlayCount(songId: Long, count: Int) {
        sharedPreferences.edit().putInt(songId.toString(), count).apply()
    }

    fun getAllPlayCounts(): Map<Long, Int> {
        return sharedPreferences.all.mapKeys { it.key.toLong() }.mapValues { it.value as Int }
    }
} 