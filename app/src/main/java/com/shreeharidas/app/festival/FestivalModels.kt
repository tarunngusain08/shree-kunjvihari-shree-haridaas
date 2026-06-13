package com.shreeharidas.app.festival

import java.time.LocalDate

enum class HinduMonth(val displayName: String, val order: Int) {
    CHAITRA("Chaitra", 1),
    VAISHAKH("Vaishakh", 2),
    JYESHTHA("Jyeshtha", 3),
    ASHADH("Ashadh", 4),
    SHRAVAN("Shravan", 5),
    BHADRAPAD("Bhadrapad", 6),
    ASHWIN("Ashwin", 7),
    KARTIK("Kartik", 8),
    AGAHAN("Agahan", 9),
    PAUSH("Paush", 10),
    MAGH("Magh", 11),
    PHALGUN("Phalgun", 12)
}

enum class Paksha(val displayName: String) {
    SHUKLA("Shukla"),
    KRISHNA("Krishna")
}

enum class FestivalSpecialType {
    VASANT_PANCHAMI,
    HOLI_PURNIMA,
    AKSHAYA_TRITIYA,
    GURU_PURNIMA
}

data class FestivalDefinition(
    val id: String,
    val name: String,
    val month: HinduMonth,
    val paksha: Paksha?,
    val tithi: Int?,
    val specialType: FestivalSpecialType?
) {
    val hinduDateLabel: String
        get() = listOfNotNull(
            month.displayName,
            paksha?.displayName,
            tithi?.toString()
        ).joinToString(" ")
}

data class FestivalOccurrence(
    val festivalId: String,
    val date: LocalDate,
    val source: String,
    val generatedAt: String
) {
    val year: Int get() = date.year
}

data class FestivalCalendarItem(
    val definition: FestivalDefinition,
    val occurrence: FestivalOccurrence?,
    val daysUntil: Long?
)

data class FestivalMonthGroup(
    val title: String,
    val items: List<FestivalCalendarItem>
)

enum class FestivalDataStatus {
    AVAILABLE,
    UNAVAILABLE,
    INVALID
}

enum class FestivalNotificationStatus {
    ENABLED,
    DISABLED,
    DATA_UNAVAILABLE,
    NOTIFICATION_PERMISSION_MISSING,
    EXACT_ALARM_PERMISSION_MISSING
}

data class FestivalUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val selectedYear: Int = LocalDate.now().year,
    val availableYears: List<Int> = emptyList(),
    val groups: List<FestivalMonthGroup> = emptyList(),
    val upcoming: List<FestivalCalendarItem> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val notificationStatus: FestivalNotificationStatus =
        FestivalNotificationStatus.DATA_UNAVAILABLE,
    val dataStatus: FestivalDataStatus = FestivalDataStatus.UNAVAILABLE,
    val message: String? = null,
    val selectedItem: FestivalCalendarItem? = null
)

data class FestivalPreferences(
    val notificationsEnabled: Boolean = true,
    val lastSyncEpochMillis: Long = 0L,
    val lastScheduleEpochMillis: Long = 0L,
    val scheduledAlertKeys: Set<String> = emptySet(),
    val firedAlertKeys: Set<String> = emptySet()
)
