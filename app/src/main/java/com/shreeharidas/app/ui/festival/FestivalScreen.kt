package com.shreeharidas.app.ui.festival

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shreeharidas.app.R
import com.shreeharidas.app.festival.FestivalCalendarItem
import com.shreeharidas.app.festival.FestivalNotificationStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalScreen(
    onBack: () -> Unit,
    viewModel: FestivalViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.title_festival_calendar),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FestivalNotificationCard(
                    status = uiState.notificationStatus,
                    notificationsEnabled = uiState.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onSearchChanged,
                    label = { Text(stringResource(R.string.label_festival_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                YearSelector(
                    year = uiState.selectedYear,
                    hasData = uiState.availableYears.isNotEmpty(),
                    onPrevious = viewModel::previousYear,
                    onNext = viewModel::nextYear
                )
            }

            uiState.message?.let { message ->
                item {
                    EmptyFestivalState(message = message)
                }
            }

            uiState.groups.forEach { group ->
                item {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(group.items) { item ->
                    FestivalRow(
                        item = item,
                        onClick = { viewModel.selectItem(item) }
                    )
                }
            }

            if (uiState.upcoming.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.label_upcoming_festivals),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(uiState.upcoming) { item ->
                    FestivalRow(
                        item = item,
                        onClick = { viewModel.selectItem(item) }
                    )
                }
            }
        }
    }

    uiState.selectedItem?.let { item ->
        ModalBottomSheet(onDismissRequest = viewModel::clearSelectedItem) {
            FestivalDetailSheet(item = item)
        }
    }
}

@Composable
private fun FestivalNotificationCard(
    status: FestivalNotificationStatus,
    notificationsEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_festival_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (status) {
                        FestivalNotificationStatus.ENABLED ->
                            stringResource(R.string.label_festival_notifications_enabled)
                        FestivalNotificationStatus.DISABLED ->
                            stringResource(R.string.label_festival_notifications_disabled)
                        FestivalNotificationStatus.DATA_UNAVAILABLE ->
                            stringResource(R.string.label_festival_data_unavailable)
                        FestivalNotificationStatus.NOTIFICATION_PERMISSION_MISSING ->
                            stringResource(R.string.label_notification_permission_missing)
                        FestivalNotificationStatus.EXACT_ALARM_PERMISSION_MISSING ->
                            stringResource(R.string.label_exact_alarm_permission_missing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = onCheckedChange,
                enabled = status != FestivalNotificationStatus.DATA_UNAVAILABLE
            )
        }
    }
}

@Composable
private fun YearSelector(
    year: Int,
    hasData: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onPrevious, enabled = hasData) {
            Text(stringResource(R.string.btn_previous_year))
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        OutlinedButton(onClick = onNext, enabled = hasData) {
            Text(stringResource(R.string.btn_next_year))
        }
    }
}

@Composable
private fun EmptyFestivalState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.title_festival_empty),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FestivalRow(
    item: FestivalCalendarItem,
    onClick: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    val occurrence = item.occurrence
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.definition.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.definition.hinduDateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (occurrence != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = occurrence.date.format(formatter),
                    style = MaterialTheme.typography.bodyMedium
                )
                item.daysUntil?.let { days ->
                    Text(
                        text = when {
                            days == 0L -> stringResource(R.string.label_festival_today)
                            days == 1L -> stringResource(R.string.label_festival_tomorrow)
                            days > 1L -> stringResource(
                                R.string.label_festival_days_until,
                                days
                            )
                            else -> stringResource(R.string.label_festival_past)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FestivalDetailSheet(item: FestivalCalendarItem) {
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.definition.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = item.definition.hinduDateLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        item.occurrence?.let { occurrence ->
            Text(
                text = stringResource(
                    R.string.label_festival_gregorian_date,
                    occurrence.date.format(formatter)
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.label_festival_source, occurrence.source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
