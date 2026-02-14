package com.shreeharidas.app.ui.components

import android.Manifest
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shreeharidas.app.R
import com.shreeharidas.app.data.SoundRepository
import com.shreeharidas.app.util.PermissionUtils
import kotlinx.coroutines.delay
import java.io.File

/**
 * Dialog for recording a custom notification sound.
 * Features: record/stop, timer, preview, save/discard with clear labels.
 */
@Composable
fun AudioRecorderDialog(
    onSaved: (filePath: String) -> Unit,
    onPreview: (String) -> Unit,
    onStopPreview: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val soundRepository = remember { SoundRepository(context) }

    var hasPermission by remember {
        mutableStateOf(PermissionUtils.hasRecordAudioPermission(context))
    }
    var isRecording by remember { mutableStateOf(false) }
    var hasRecording by remember { mutableStateOf(false) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Request permission if needed
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Cleanup on dismiss
    DisposableEffect(Unit) {
        onDispose {
            try {
                recorder?.let {
                    if (isRecording) {
                        it.stop()
                    }
                    it.release()
                }
            } catch (_: Exception) {}
            onStopPreview()
        }
    }

    /** Helper to get a proper Android Uri from the recording file. */
    fun getRecordingUri(): String {
        return recordingFile?.let { Uri.fromFile(it).toString() } ?: ""
    }

    AlertDialog(
        onDismissRequest = {
            // Cleanup recording if in progress
            if (isRecording) {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {}
                isRecording = false
            }
            onStopPreview()
            // Delete file if not saved
            if (!hasRecording) {
                recordingFile?.delete()
            }
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.title_recorder),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!hasPermission) {
                    Text(
                        text = stringResource(R.string.permission_audio_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    return@Column
                }

                // Timer display
                val minutes = recordingSeconds / 60
                val seconds = recordingSeconds % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light,
                    color = if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                // Status text
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isRecording -> stringResource(R.string.label_recording)
                        hasRecording -> stringResource(
                            R.string.label_recording_saved
                        )
                        else -> stringResource(R.string.label_tap_record)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isRecording -> MaterialTheme.colorScheme.error
                        hasRecording -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // === STATE: Ready to record (no recording yet) ===
                if (!isRecording && !hasRecording) {
                    Button(
                        onClick = {
                            try {
                                val (rec, file) = soundRepository.createRecorder()
                                rec.prepare()
                                rec.start()
                                recorder = rec
                                recordingFile = file
                                isRecording = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onError)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_record),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // === STATE: Currently recording ===
                if (isRecording) {
                    Button(
                        onClick = {
                            try {
                                recorder?.stop()
                                recorder?.release()
                                recorder = null
                                isRecording = false
                                hasRecording = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isRecording = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(MaterialTheme.colorScheme.onPrimary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_stop_recording),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // === STATE: Recording finished, review ===
                if (hasRecording && !isRecording) {
                    // Play / Stop Preview button
                    FilledTonalButton(
                        onClick = {
                            if (isPreviewPlaying) {
                                onStopPreview()
                                isPreviewPlaying = false
                            } else {
                                val uri = getRecordingUri()
                                if (uri.isNotEmpty()) {
                                    onPreview(uri)
                                    isPreviewPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPreviewPlaying) {
                                stringResource(R.string.btn_stop_preview)
                            } else {
                                stringResource(R.string.btn_play_preview)
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Record again / Discard row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                onStopPreview()
                                isPreviewPlaying = false
                                recordingFile?.delete()
                                recordingFile = null
                                hasRecording = false
                                recordingSeconds = 0
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_re_record))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasRecording && !isRecording) {
                Button(
                    onClick = {
                        onStopPreview()
                        recordingFile?.let { file ->
                            onSaved(file.absolutePath)
                        }
                        onDismiss()
                    }
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onStopPreview()
                    if (isRecording) {
                        try {
                            recorder?.stop()
                            recorder?.release()
                        } catch (_: Exception) {}
                        isRecording = false
                    }
                    recordingFile?.delete()
                    onDismiss()
                }
            ) {
                Text(
                    if (hasRecording) {
                        stringResource(R.string.btn_discard)
                    } else {
                        stringResource(R.string.btn_cancel)
                    }
                )
            }
        }
    )
}
