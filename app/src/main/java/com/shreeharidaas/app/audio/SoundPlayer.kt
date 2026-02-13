package com.shreeharidaas.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.shreeharidaas.app.data.SoundRepository

/**
 * Wrapper around MediaPlayer for playing reminder sounds.
 * Routes audio to STREAM_ALARM so it always plays audibly
 * regardless of DND mode or notification volume settings.
 * Handles URI validation and fallback to default bell sound.
 */
class SoundPlayer(private val context: Context) {

    companion object {
        private const val TAG = "SoundPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private val soundRepository = SoundRepository(context)

    /**
     * Audio attributes using USAGE_ALARM to ensure sound is always
     * audible even when DND is active or notification volume is low.
     */
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Play a sound from the given URI string.
     * Falls back to default bell if the URI is invalid or inaccessible.
     */
    fun play(uriString: String) {
        // Reset any existing playback to prevent overlap
        stop()

        Log.d(TAG, "Playing sound: $uriString")

        // Try playing as raw resource first (most reliable for defaults)
        val resId = soundRepository.resolveRawResourceId(uriString)
        if (resId != null) {
            try {
                playRawResource(resId)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play raw resource: $uriString", e)
            }
        }

        // Try playing as URI
        val uri = soundRepository.resolveUri(uriString)
        if (uri != null) {
            try {
                playUri(uri)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play sound URI: $uriString", e)
            }
        }

        // Fallback to default bell
        Log.w(TAG, "Falling back to default bell sound")
        try {
            playRawResource(com.shreeharidaas.app.R.raw.bell)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play fallback sound", e)
        }
    }

    /**
     * Play a raw resource directly using AssetFileDescriptor.
     * This is the most reliable method for bundled sounds.
     */
    private fun playRawResource(resId: Int) {
        val afd = context.resources.openRawResourceFd(resId)
            ?: throw IllegalStateException("Cannot open raw resource: $resId")

        val player = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            prepare()
            setOnCompletionListener { mp ->
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
            }
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
                true
            }
        }

        // Ensure alarm volume is audible
        ensureVolumeAudible()

        mediaPlayer = player
        player.start()
        Log.d(TAG, "Started playing raw resource: $resId")
    }

    /**
     * Play a sound from a parsed Uri (for recordings and picked files).
     */
    private fun playUri(uri: Uri) {
        Log.d(TAG, "Playing URI: $uri")
        val player = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(context, uri)
            prepare()
            setOnCompletionListener { mp ->
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
            }
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                mp.release()
                if (mediaPlayer == mp) {
                    mediaPlayer = null
                }
                true
            }
        }

        // Ensure alarm volume is audible
        ensureVolumeAudible()

        mediaPlayer = player
        player.start()
        Log.d(TAG, "Started playing URI: $uri")
    }

    /**
     * Ensure the alarm stream volume is at a reasonable level.
     * If it's at 0, bump it to ~60% so the user can hear the sound.
     */
    private fun ensureVolumeAudible() {
        try {
            val audioManager = context.getSystemService(
                Context.AUDIO_SERVICE
            ) as AudioManager
            val current = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (current == 0) {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    (max * 0.6).toInt().coerceAtLeast(1),
                    0
                )
                Log.d(TAG, "Alarm volume was 0, bumped to ${(max * 0.6).toInt()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not check/set alarm volume", e)
        }
    }

    /**
     * Stop any currently playing sound and release resources.
     */
    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping media player", e)
        }
        mediaPlayer = null
    }

    /**
     * Check if a sound is currently playing.
     */
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Release all resources. Call when done with this player.
     */
    fun release() {
        stop()
    }
}
