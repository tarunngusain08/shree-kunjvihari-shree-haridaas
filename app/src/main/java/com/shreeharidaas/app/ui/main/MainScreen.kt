package com.shreeharidaas.app.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreeharidaas.app.R
import com.shreeharidaas.app.data.SoundType
import com.shreeharidaas.app.service.AlarmScheduler
import com.shreeharidaas.app.ui.components.AudioRecorderDialog
import com.shreeharidaas.app.ui.components.FrequencyInput
import com.shreeharidaas.app.ui.components.SoundPickerDialog
import com.shreeharidaas.app.util.PermissionUtils

/**
 * Main screen with frequency input, sound selector, status, and start/stop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsState()
    val frequencyInput by viewModel.frequencyInput.collectAsState()
    val frequencyError by viewModel.frequencyError.collectAsState()
    val countdownText by viewModel.countdownText.collectAsState()
    val showSoundPicker by viewModel.showSoundPicker.collectAsState()
    val showAudioRecorder by viewModel.showAudioRecorder.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val alarmScheduler = remember { AlarmScheduler(context) }

    // Request notification permission on first launch (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onFilePicked(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_main),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.title_settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sound selector card
            Card(
                onClick = {
                    if (!preferences.isRunning) {
                        viewModel.showSoundPicker()
                    }
                },
                enabled = !preferences.isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        .copy(alpha = 0.6f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.label_sound),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = viewModel.getSoundDisplayName(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Frequency input
            FrequencyInput(
                value = frequencyInput,
                onValueChange = viewModel::onFrequencyChanged,
                errorMessage = frequencyError,
                enabled = !preferences.isRunning
            )

            // Active time window card
            ActiveTimeWindowCard(
                enabled = !preferences.isRunning,
                activeWindowEnabled = preferences.activeWindowEnabled,
                startHour = preferences.startHour,
                startMinute = preferences.startMinute,
                endHour = preferences.endHour,
                endMinute = preferences.endMinute,
                onToggle = viewModel::toggleActiveWindow,
                onStartTimeSet = viewModel::setStartTime,
                onEndTimeSet = viewModel::setEndTime,
                formatTime = viewModel::formatTime
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status indicator
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (preferences.isRunning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (preferences.isRunning) {
                            stringResource(R.string.label_status_active)
                        } else {
                            stringResource(R.string.label_status_inactive)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (preferences.isRunning) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (preferences.isRunning && countdownText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(
                                R.string.label_next_reminder,
                                countdownText
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start/Stop button
            Button(
                onClick = {
                    if (preferences.isRunning) {
                        viewModel.stopReminder()
                    } else {
                        // Check exact alarm permission
                        if (!alarmScheduler.canScheduleExactAlarms()) {
                            context.startActivity(
                                PermissionUtils.exactAlarmSettingsIntent(context)
                            )
                            return@Button
                        }
                        viewModel.startReminder()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = preferences.isRunning || frequencyError == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (preferences.isRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    text = if (preferences.isRunning) {
                        stringResource(R.string.btn_stop)
                    } else {
                        stringResource(R.string.btn_start)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Sound Picker Dialog
    if (showSoundPicker) {
        SoundPickerDialog(
            currentSoundUri = preferences.soundUri,
            currentSoundType = preferences.soundType,
            defaultSounds = viewModel.defaultSounds,
            onSoundSelected = viewModel::selectSound,
            onRecordRequested = {
                viewModel.hideSoundPicker()
                viewModel.showAudioRecorder()
            },
            onFilePickRequested = {
                viewModel.hideSoundPicker()
                filePickerLauncher.launch(
                    arrayOf(
                        "audio/mpeg",
                        "audio/wav",
                        "audio/ogg",
                        "audio/mp4",
                        "audio/x-wav",
                        "audio/x-m4a"
                    )
                )
            },
            onPreview = viewModel::previewSound,
            onStopPreview = viewModel::stopPreview,
            isPlaying = viewModel.soundPlayer.isPlaying(),
            onDismiss = viewModel::hideSoundPicker
        )
    }

    // Audio Recorder Dialog
    if (showAudioRecorder) {
        AudioRecorderDialog(
            onSaved = { filePath ->
                viewModel.onRecordingSaved(filePath)
            },
            onPreview = viewModel::previewSound,
            onStopPreview = viewModel::stopPreview,
            onDismiss = viewModel::hideAudioRecorder
        )
    }
}

/**
 * Card for configuring the active time window (start/end hours).
 * Default is all day. Users can toggle a custom window with time pickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveTimeWindowCard(
    enabled: Boolean,
    activeWindowEnabled: Boolean,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onToggle: (Boolean) -> Unit,
    onStartTimeSet: (Int, Int) -> Unit,
    onEndTimeSet: (Int, Int) -> Unit,
    formatTime: (Int, Int) -> String
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row with toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.label_active_window),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (activeWindowEnabled) {
                            stringResource(
                                R.string.label_active_window_range,
                                formatTime(startHour, startMinute),
                                formatTime(endHour, endMinute)
                            )
                        } else {
                            stringResource(R.string.label_active_window_all_day)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = activeWindowEnabled,
                    onCheckedChange = { onToggle(it) },
                    enabled = enabled
                )
            }

            // Expand time selectors when enabled
            AnimatedVisibility(visible = activeWindowEnabled) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_active_window_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Start time row
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { showStartPicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_start_time),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatTime(startHour, startMinute),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // End time row
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { showEndPicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.label_end_time),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatTime(endHour, endMinute),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // Time Picker Dialogs
    if (showStartPicker) {
        TimePickerDialogWrapper(
            title = stringResource(R.string.label_start_time),
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m ->
                onStartTimeSet(h, m)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        TimePickerDialogWrapper(
            title = stringResource(R.string.label_end_time),
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m ->
                onEndTimeSet(h, m)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

/**
 * Wrapper around Material3 TimePicker in a dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogWrapper(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            TimePicker(state = state)
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.btn_ok))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
