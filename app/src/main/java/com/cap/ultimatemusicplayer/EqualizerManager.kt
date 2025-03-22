package com.cap.ultimatemusicplayer

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.util.Log
import android.widget.Toast
import java.io.Serializable
import java.util.HashMap

class EqualizerManager(private val context: Context) {
    private var equalizer: Equalizer? = null
    private var audioSessionId: Int = 0
    private val bandLevels = HashMap<Short, Short>()  // band -> level

    fun setupEqualizer(sessionId: Int): Boolean {
        try {
            Log.d("Equalizer", "Setting up equalizer with session ID: $sessionId")
            
            // Release existing equalizer if any
            release()
            
            // Store audio session ID
            audioSessionId = sessionId
            
            // Create and setup new equalizer with priority 0 (default)
            equalizer = Equalizer(0, sessionId)
            
            // Set enabled state and verify
            equalizer?.let { eq ->
                // Set enabled state
                eq.enabled = true
                
                // Verify if equalizer is actually supported and enabled
                val numberOfBands = eq.numberOfBands
                val numberOfPresets = eq.numberOfPresets
                Log.d("Equalizer", "Created equalizer instance, enabled: ${eq.enabled}, number of bands: $numberOfBands, number of presets: $numberOfPresets")
                
                if (numberOfBands > 0) {
                    // Initialize default band levels
                    for (i in 0 until numberOfBands) {
                        val band = i.toShort()
                        val level = eq.getBandLevel(band)
                        bandLevels[band] = level
                        Log.d("Equalizer", "Band $i initialized with level $level")
                    }
                    
                    // Log band level range
                    val minLevel = eq.bandLevelRange[0]
                    val maxLevel = eq.bandLevelRange[1]
                    Log.d("Equalizer", "Band level range: $minLevel to $maxLevel")
                    
                    // Log center frequencies
                    for (i in 0 until numberOfBands) {
                        val band = i.toShort()
                        val centerFreq = eq.getCenterFreq(band)
                        Log.d("Equalizer", "Band $i center frequency: $centerFreq Hz")
                    }
                    
                    // Also try to connect to the active music service
                    tryConnectToMusicService()
                    
                    // Notify user that equalizer is ready
                    Toast.makeText(context, "Equalizer ready", Toast.LENGTH_SHORT).show()
                    
                    return true
                } else {
                    throw Exception("Equalizer has no bands")
                }
            } ?: run {
                Log.e("Equalizer", "Failed to create equalizer instance")
                return false
            }
        } catch (e: Exception) {
            Log.e("Equalizer", "Error setting up equalizer: ${e.message}")
            e.printStackTrace()
            
            // Try a fallback approach - create a new instance with a different priority
            try {
                Log.d("Equalizer", "Trying fallback equalizer setup with priority 10000")
                equalizer = Equalizer(10000, sessionId)
                equalizer?.let { eq ->
                    // Set enabled state
                    eq.enabled = true
                    
                    val fallbackBands = eq.numberOfBands
                    if (fallbackBands > 0) {
                        Log.d("Equalizer", "Fallback equalizer setup successful")
                        // Initialize default band levels
                        for (i in 0 until fallbackBands) {
                            val band = i.toShort()
                            val level = eq.getBandLevel(band)
                            bandLevels[band] = level
                        }
                        
                        // Also try to connect to the active music service
                        tryConnectToMusicService()
                        
                        Toast.makeText(context, "Equalizer ready", Toast.LENGTH_SHORT).show()
                        return true
                    } else {
                        throw Exception("Fallback equalizer has no bands")
                    }
                } ?: throw Exception("Failed to create fallback equalizer instance")
            } catch (fallbackError: Exception) {
                Log.e("Equalizer", "Fallback equalizer setup failed: ${fallbackError.message}")
                fallbackError.printStackTrace()
                equalizer = null
                Toast.makeText(context, "Equalizer not available on this device", Toast.LENGTH_SHORT).show()
                return false
            }
        }
    }
    
    private fun tryConnectToMusicService() {
        try {
            // Request the audio session ID from the music service
            val intent = Intent("com.cap.ultimatemusicplayer.GET_AUDIO_SESSION_ID")
            context.sendBroadcast(intent)
            Log.d("Equalizer", "Sent request to connect to music service")
        } catch (e: Exception) {
            Log.e("Equalizer", "Failed to connect to music service: ${e.message}")
        }
    }

    fun setBandLevel(band: Short, level: Short) {
        try {
            equalizer?.setBandLevel(band, level)
            bandLevels[band] = level
            
            // Send the updated settings to the MusicService
            sendEqualizerSettingsToService()
            
            Log.d("Equalizer", "Set band $band level to $level")
        } catch (e: Exception) {
            Log.e("Equalizer", "Error setting band level: ${e.message}")
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            
            // Send the updated settings to the MusicService
            sendEqualizerSettingsToService()
            
            Log.d("Equalizer", "Set equalizer enabled to $enabled")
        } catch (e: Exception) {
            Log.e("Equalizer", "Error setting equalizer enabled: ${e.message}")
        }
    }
    
    private fun sendEqualizerSettingsToService() {
        try {
            val intent = Intent(context, MusicService::class.java).apply {
                action = "APPLY_EQUALIZER_SETTINGS"
                putExtra("bandLevels", bandLevels as Serializable)
                putExtra("enabled", equalizer?.enabled ?: false)
            }
            context.startService(intent)
            Log.d("Equalizer", "Sent equalizer settings to MusicService")
        } catch (e: Exception) {
            Log.e("Equalizer", "Error sending equalizer settings to service: ${e.message}")
        }
    }

    fun getBandLevel(band: Short): Short {
        return try {
            equalizer?.let { eq ->
                if (!eq.enabled) {
                    eq.enabled = true
                    if (!eq.enabled) {
                        throw Exception("Failed to enable equalizer")
                    }
                }
                val level = eq.getBandLevel(band)
                Log.d("Equalizer", "Got band $band level: $level")
                level
            } ?: throw Exception("Equalizer not initialized")
        } catch (e: Exception) {
            Log.e("Equalizer", "Failed to get band level: ${e.message}")
            bandLevels[band] ?: 0 // Return cached level or 0 if not available
        }
    }

    fun getBandLevelRange(): Pair<Short, Short> {
        return try {
            equalizer?.let { eq ->
                if (!eq.enabled) {
                    eq.enabled = true
                    if (!eq.enabled) {
                        throw Exception("Failed to enable equalizer")
                    }
                }
                val range = eq.bandLevelRange
                Log.d("Equalizer", "Band level range: ${range[0]} to ${range[1]}")
                Pair(range[0], range[1])
            } ?: throw Exception("Equalizer not initialized")
        } catch (e: Exception) {
            Log.e("Equalizer", "Failed to get band level range: ${e.message}")
            Pair(-1500, 1500) // Return default range on error
        }
    }

    fun getNumberOfBands(): Int {
        return try {
            equalizer?.let { eq ->
                if (!eq.enabled) {
                    eq.enabled = true
                    if (!eq.enabled) {
                        throw Exception("Failed to enable equalizer")
                    }
                }
                val bands = eq.numberOfBands.toInt()
                Log.d("Equalizer", "Number of bands: $bands")
                bands
            } ?: throw Exception("Equalizer not initialized")
        } catch (e: Exception) {
            Log.e("Equalizer", "Failed to get number of bands: ${e.message}")
            5 // Return default number of bands on error
        }
    }

    fun getCenterFreq(band: Short): Int {
        return try {
            equalizer?.let { eq ->
                if (!eq.enabled) {
                    eq.enabled = true
                    if (!eq.enabled) {
                        throw Exception("Failed to enable equalizer")
                    }
                }
                val freq = eq.getCenterFreq(band)
                Log.d("Equalizer", "Center frequency for band $band: $freq")
                freq
            } ?: throw Exception("Equalizer not initialized")
        } catch (e: Exception) {
            Log.e("Equalizer", "Failed to get center frequency: ${e.message}")
            when (band.toInt()) { // Return default frequencies on error
                0 -> 60000
                1 -> 230000
                2 -> 910000
                3 -> 3600000
                4 -> 14000000
                else -> 0
            }
        }
    }

    fun resetBands() {
        try {
            equalizer?.let { eq ->
                if (!eq.enabled) {
                    eq.enabled = true
                    if (!eq.enabled) {
                        throw Exception("Failed to enable equalizer")
                    }
                }
                val numberOfBands = eq.numberOfBands
                for (i in 0 until numberOfBands) {
                    eq.setBandLevel(i.toShort(), 0)
                    bandLevels[i.toShort()] = 0
                }
                Log.d("Equalizer", "Reset all bands to 0")
            } ?: throw Exception("Equalizer not initialized")
        } catch (e: Exception) {
            Log.e("Equalizer", "Failed to reset equalizer: ${e.message}")
            e.printStackTrace()
            throw e // Re-throw to notify caller
        }
    }

    fun release() {
        try {
            equalizer?.let { eq ->
                eq.enabled = false
                eq.release()
                Log.d("Equalizer", "Released equalizer")
            }
            equalizer = null
            audioSessionId = 0
            bandLevels.clear()
        } catch (e: Exception) {
            Log.e("Equalizer", "Error releasing equalizer: ${e.message}")
            e.printStackTrace()
        }
    }

    fun isAvailable(): Boolean {
        return try {
            equalizer?.let { eq ->
                val isEnabled = eq.enabled
                val hasValidSession = audioSessionId != 0
                val hasBands = eq.numberOfBands > 0
                val available = isEnabled && hasValidSession && hasBands
                
                Log.d("Equalizer", """Checking availability: $available
                    |  - Session ID: $audioSessionId
                    |  - Equalizer enabled: $isEnabled
                    |  - Has bands: $hasBands
                    |  - Number of bands: ${eq.numberOfBands}""".trimMargin())
                
                available
            } ?: false
        } catch (e: Exception) {
            Log.e("Equalizer", "Error checking equalizer availability: ${e.message}")
            false
        }
    }

    fun getMinBandLevel(): Short {
        return equalizer?.bandLevelRange?.get(0) ?: 0
    }
    
    fun getMaxBandLevel(): Short {
        return equalizer?.bandLevelRange?.get(1) ?: 0
    }
    
    fun getBandLevels(): HashMap<Short, Short> {
        return HashMap(bandLevels)
    }
    
    fun isEnabled(): Boolean {
        return equalizer?.enabled ?: false
    }
} 