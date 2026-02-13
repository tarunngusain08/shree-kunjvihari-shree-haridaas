package com.shreeharidaas.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shreeharidaas.app.R
import com.shreeharidaas.app.data.SoundOption
import com.shreeharidaas.app.data.SoundType
import com.shreeharidaas.app.util.PermissionUtils

/**
 * Dialog for choosing a notification sound:
 * - Built-in defaults with preview
 * - Record new sound
 * - Choose from files
 */
@Composable
fun SoundPickerDialog(
    currentSoundUri: String,
    currentSoundType: SoundType,
    defaultSounds: List<SoundOption>,
    onSoundSelected: (uri: String, type: SoundType) -> Unit,
    onRecordRequested: () -> Unit,
    onFilePickRequested: () -> Unit,
    onPreview: (String) -> Unit,
    onStopPreview: () -> Unit,
    isPlaying: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playingUri by remember { mutableStateOf<String?>(null) }

    // File picker permission launcher
    val readAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onFilePickRequested()
        }
    }

    AlertDialog(
        onDismissRequest = {
            onStopPreview()
            onDismiss()
        },
        title = {
            Text(
                text = stringResource(R.string.title_sound_picker),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                // Default sounds
                defaultSounds.forEach { sound ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSoundSelected(sound.uri, SoundType.DEFAULT)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = currentSoundUri == sound.uri,
                            onClick = {
                                onSoundSelected(sound.uri, SoundType.DEFAULT)
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Text(
                            text = "${sound.name} (${stringResource(R.string.label_default)})",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        IconButton(
                            onClick = {
                                if (playingUri == sound.uri) {
                                    onStopPreview()
                                    playingUri = null
                                } else {
                                    onPreview(sound.uri)
                                    playingUri = sound.uri
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (playingUri == sound.uri && isPlaying) {
                                    Icons.Default.Close
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = stringResource(R.string.btn_preview)
                            )
                        }
                    }
                }

                // Show current custom selection if applicable
                if (currentSoundType == SoundType.RECORDED ||
                    currentSoundType == SoundType.FILE
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = true,
                            onClick = {}
                        )
                        Icon(
                            imageVector = if (currentSoundType == SoundType.RECORDED) {
                                Icons.Default.Create
                            } else {
                                Icons.Default.Add
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.label_currently_selected),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (currentSoundType == SoundType.RECORDED) {
                                    stringResource(R.string.label_recorded)
                                } else {
                                    stringResource(R.string.label_custom_file)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        IconButton(
                            onClick = {
                                if (playingUri == currentSoundUri) {
                                    onStopPreview()
                                    playingUri = null
                                } else {
                                    onPreview(currentSoundUri)
                                    playingUri = currentSoundUri
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (playingUri == currentSoundUri && isPlaying) {
                                    Icons.Default.Close
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = stringResource(R.string.btn_preview)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Record new sound button
                FilledTonalButton(
                    onClick = {
                        onStopPreview()
                        onRecordRequested()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Create, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_record_new))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Choose from files button
                FilledTonalButton(
                    onClick = {
                        onStopPreview()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !PermissionUtils.hasReadMediaAudioPermission(context)
                        ) {
                            readAudioPermissionLauncher.launch(
                                Manifest.permission.READ_MEDIA_AUDIO
                            )
                        } else {
                            onFilePickRequested()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_choose_file))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onStopPreview()
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_ok))
            }
        }
    )
}
