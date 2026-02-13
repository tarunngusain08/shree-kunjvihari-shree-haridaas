package com.shreeharidaas.app.ui.components

import android.Manifest
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shreeharidaas.app.R
import com.shreeharidaas.app.data.SoundRepository
import com.shreeharidaas.app.util.PermissionUtils
import kotlinx.coroutines.delay
import java.io.File

/**
 * Dialog for recording a custom notification sound.
 * Features: record/stop, timer, preview, save/discard.
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
                        color = MaterialTheme.colorScheme.error
                    )
                    return@Column
                }

                // Timer display
                val minutes = recordingSeconds / 60
                val seconds = recordingSeconds % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (isRecording) {
                    Text(
                        text = stringResource(R.string.label_recording),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controls
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!isRecording && !hasRecording) {
                        // Record button
                        FilledIconButton(
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
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Text(
                                "●",
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }

                    if (isRecording) {
                        // Stop button
                        FilledIconButton(
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
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(
                                    R.string.btn_stop_recording
                                ),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    if (hasRecording) {
                        // Preview button
                        FilledIconButton(
                            onClick = {
                                recordingFile?.let {
                                    onPreview(it.toURI().toString())
                                }
                            },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(
                                    R.string.btn_play_preview
                                ),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasRecording) {
                TextButton(
                    onClick = {
                        onStopPreview()
                        recordingFile?.let { file ->
                            onSaved(file.absolutePath)
                        }
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onStopPreview()
                    if (!hasRecording) {
                        recordingFile?.delete()
                    }
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
