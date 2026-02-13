package com.shreeharidaas.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreeharidaas.app.R
import com.shreeharidaas.app.util.PermissionUtils

/**
 * Settings screen with DND override, vibration, battery optimization,
 * notification volume info, and about section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val preferences by viewModel.preferences.collectAsState()
    val isDndPermissionGranted by viewModel.isDndPermissionGranted.collectAsState()
    val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh permission states on resume
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.currentStateFlow.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                viewModel.refreshPermissionStates()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_settings),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // DND Override
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_dnd_title))
                },
                supportingContent = {
                    Text(
                        if (!isDndPermissionGranted && preferences.dndOverride) {
                            stringResource(R.string.setting_dnd_permission_needed)
                        } else {
                            stringResource(R.string.setting_dnd_subtitle)
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = preferences.dndOverride,
                        onCheckedChange = { enabled ->
                            val needsPermission = viewModel.toggleDndOverride(enabled)
                            if (needsPermission) {
                                context.startActivity(
                                    PermissionUtils.dndAccessSettingsIntent()
                                )
                            }
                        }
                    )
                }
            )

            HorizontalDivider()

            // Vibration
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_vibration_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.setting_vibration_subtitle))
                },
                trailingContent = {
                    Switch(
                        checked = preferences.vibrationEnabled,
                        onCheckedChange = viewModel::toggleVibration
                    )
                }
            )

            HorizontalDivider()

            // Battery Optimization
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_battery_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.setting_battery_subtitle))
                }
            )
            FilledTonalButton(
                onClick = {
                    context.startActivity(
                        PermissionUtils.batteryOptimizationIntent(context)
                    )
                },
                enabled = isBatteryOptimized,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = if (isBatteryOptimized) {
                        stringResource(R.string.setting_battery_btn)
                    } else {
                        "Battery optimization already disabled"
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // Notification Volume (read-only info)
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_volume_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.setting_volume_subtitle))
                }
            )

            HorizontalDivider()

            // About
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_about_title))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.setting_about_version,
                            viewModel.getAppVersion()
                        )
                    )
                }
            )
        }
    }
}
