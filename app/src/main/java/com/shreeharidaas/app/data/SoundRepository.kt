package com.shreeharidaas.app.data

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.shreeharidaas.app.R
import com.shreeharidaas.app.util.Constants
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a sound option available for selection.
 */
data class SoundOption(
    val name: String,
    val uri: String,
    val type: SoundType,
    val isDefault: Boolean = false
)

/**
 * Repository for managing sound assets: defaults, recordings, and picked files.
 */
class SoundRepository(private val context: Context) {

    /** Returns the list of built-in default sounds. */
    fun getDefaultSounds(): List<SoundOption> {
        return listOf(
            SoundOption(
                name = "Bell",
                uri = getDefaultSoundUri(R.raw.bell),
                type = SoundType.DEFAULT,
                isDefault = true
            ),
            SoundOption(
                name = "Chime",
                uri = getDefaultSoundUri(R.raw.chime),
                type = SoundType.DEFAULT,
                isDefault = true
            ),
            SoundOption(
                name = "Beep",
                uri = getDefaultSoundUri(R.raw.beep),
                type = SoundType.DEFAULT,
                isDefault = true
            )
        )
    }

    /** Builds the android.resource:// URI for a raw resource. */
    private fun getDefaultSoundUri(resId: Int): String {
        return "android.resource://${context.packageName}/$resId"
    }

    /** Returns the URI string for the default bell sound (fallback). */
    fun getDefaultBellUri(): String {
        return getDefaultSoundUri(R.raw.bell)
    }

    /** Returns the recordings directory, creating it if needed. */
    private fun getRecordingsDir(): File {
        val dir = File(context.filesDir, Constants.RECORDINGS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /** Generates a unique filename for a new recording. */
    private fun generateRecordingFilename(): String {
        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())
        return "recording_$timestamp.m4a"
    }

    /**
     * Creates and configures a MediaRecorder for recording audio.
     * Returns the recorder and the output file path.
     */
    fun createRecorder(): Pair<MediaRecorder, File> {
        val file = File(getRecordingsDir(), generateRecordingFilename())
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
        }

        return Pair(recorder, file)
    }

    /**
     * Try to resolve a URI string to a raw resource ID.
     * Returns the resource ID if it's a bundled default sound, null otherwise.
     * This allows the player to use the more reliable AssetFileDescriptor path.
     */
    fun resolveRawResourceId(uriString: String): Int? {
        // Handle "default:name" format
        if (uriString.startsWith("default:")) {
            return when (uriString.removePrefix("default:")) {
                "bell" -> R.raw.bell
                "chime" -> R.raw.chime
                "beep" -> R.raw.beep
                else -> R.raw.bell
            }
        }
        // Handle "android.resource://package/resId" format
        if (uriString.startsWith("android.resource://")) {
            try {
                val resId = uriString.substringAfterLast("/").toIntOrNull()
                if (resId != null) {
                    // Verify it's one of our known raw resources
                    val knownIds = listOf(R.raw.bell, R.raw.chime, R.raw.beep)
                    if (resId in knownIds) {
                        return resId
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /** Resolves a sound URI string to a Uri, handling default: prefix. */
    fun resolveUri(uriString: String): Uri? {
        return try {
            if (uriString.startsWith("default:")) {
                val name = uriString.removePrefix("default:")
                val resId = when (name) {
                    "bell" -> R.raw.bell
                    "chime" -> R.raw.chime
                    "beep" -> R.raw.beep
                    else -> R.raw.bell
                }
                Uri.parse(getDefaultSoundUri(resId))
            } else {
                Uri.parse(uriString)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Checks if a URI is still accessible. */
    fun isUriAccessible(uriString: String): Boolean {
        val uri = resolveUri(uriString) ?: return false
        return try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (_: Exception) {
            // Check if it's a file URI
            try {
                val file = File(uri.path ?: return false)
                file.exists() && file.canRead()
            } catch (_: Exception) {
                false
            }
        }
    }

    /** Returns a display name for a given sound URI. */
    fun getSoundDisplayName(uriString: String, soundType: SoundType): String {
        return when {
            uriString.startsWith("default:") -> {
                val name = uriString.removePrefix("default:")
                name.replaceFirstChar { it.uppercase() }
            }
            uriString.contains("/raw/") -> {
                // android.resource:// URI for defaults
                val defaults = getDefaultSounds()
                defaults.find { it.uri == uriString }?.name ?: "Default Sound"
            }
            soundType == SoundType.RECORDED -> "Recorded Sound"
            soundType == SoundType.FILE -> {
                try {
                    val uri = Uri.parse(uriString)
                    uri.lastPathSegment?.substringAfterLast('/') ?: "Custom File"
                } catch (_: Exception) {
                    "Custom File"
                }
            }
            else -> "Unknown Sound"
        }
    }
}
