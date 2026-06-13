package com.shreeharidas.app.festival.data

import android.content.Context
import android.util.Log
import com.shreeharidas.app.festival.FestivalCalendarItem
import com.shreeharidas.app.festival.FestivalDataStatus
import com.shreeharidas.app.festival.FestivalDefinition
import com.shreeharidas.app.festival.FestivalMonthGroup
import com.shreeharidas.app.festival.FestivalOccurrence
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FestivalRepository(private val context: Context) {

    companion object {
        private const val TAG = "FestivalRepository"
        private const val DEFINITIONS_ASSET = "festival_definitions.json"
        private const val OCCURRENCES_ASSET = "festival_occurrences.json"
        private const val YEAR_OCCURRENCE_DIR = "festival_occurrences"

        @Volatile
        private var cachedState: FestivalRepositoryState? = null

        fun clearCacheForTests() {
            cachedState = null
        }
    }

    suspend fun loadState(): FestivalRepositoryState = withContext(Dispatchers.IO) {
        cachedState ?: synchronized(this@FestivalRepository) {
            cachedState ?: loadStateUncached().also { cachedState = it }
        }
    }

    private fun loadStateUncached(): FestivalRepositoryState {
        val definitions = when (
            val result = FestivalAssetParser.parseDefinitions(readAsset(DEFINITIONS_ASSET))
        ) {
            is FestivalParseResult.Success -> result.value
            is FestivalParseResult.Error -> {
                Log.e(TAG, result.message)
                return FestivalRepositoryState.invalid(result.message)
            }
        }

        val occurrenceResult = loadOccurrences(definitions)
        return when (occurrenceResult) {
            is FestivalParseResult.Success -> {
                val occurrences = occurrenceResult.value
                if (occurrences.isEmpty()) {
                    FestivalRepositoryState(
                        definitions = definitions,
                        occurrences = emptyList(),
                        status = FestivalDataStatus.UNAVAILABLE,
                        errors = listOf("Reviewed festival dates are not bundled yet.")
                    )
                } else {
                    FestivalRepositoryState(
                        definitions = definitions,
                        occurrences = occurrences,
                        status = FestivalDataStatus.AVAILABLE,
                        errors = emptyList()
                    )
                }
            }
            is FestivalParseResult.Error -> {
                Log.e(TAG, occurrenceResult.message)
                FestivalRepositoryState(
                    definitions = definitions,
                    occurrences = emptyList(),
                    status = FestivalDataStatus.INVALID,
                    errors = listOf(occurrenceResult.message)
                )
            }
        }
    }

    private fun loadOccurrences(
        definitions: List<FestivalDefinition>
    ): FestivalParseResult<List<FestivalOccurrence>> {
        val knownIds = definitions.map { it.id }.toSet()
        val yearFiles = listYearOccurrenceFiles()

        if (yearFiles.isNotEmpty()) {
            val years = yearFiles.map { it.removeSuffix(".json").toInt() }
            FestivalAssetParser.validateContiguousYears(years)?.let {
                return FestivalParseResult.Error(it)
            }

            val merged = mutableListOf<FestivalOccurrence>()
            for (fileName in yearFiles) {
                val assetPath = "$YEAR_OCCURRENCE_DIR/$fileName"
                when (
                    val result = FestivalAssetParser.parseOccurrences(
                        readAsset(assetPath),
                        knownIds
                    )
                ) {
                    is FestivalParseResult.Success -> merged += result.value
                    is FestivalParseResult.Error -> return result
                }
            }
            return validateMergedOccurrences(merged)
        }

        return try {
            FestivalAssetParser.parseOccurrences(readAsset(OCCURRENCES_ASSET), knownIds)
        } catch (_: FileNotFoundException) {
            FestivalParseResult.Success(emptyList())
        }
    }

    private fun validateMergedOccurrences(
        occurrences: List<FestivalOccurrence>
    ): FestivalParseResult<List<FestivalOccurrence>> {
        val keys = mutableSetOf<String>()
        for (occurrence in occurrences) {
            val key = "${occurrence.festivalId}:${occurrence.date}"
            if (!keys.add(key)) {
                return FestivalParseResult.Error(
                    "Duplicate festival occurrence across assets: $key"
                )
            }
        }
        return FestivalParseResult.Success(occurrences.sortedBy { it.date })
    }

    private fun listYearOccurrenceFiles(): List<String> {
        return try {
            context.assets.list(YEAR_OCCURRENCE_DIR)
                ?.filter { it.matches(Regex("\\d{4}\\.json")) }
                ?.sorted()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readAsset(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }
}

data class FestivalRepositoryState(
    val definitions: List<FestivalDefinition>,
    val occurrences: List<FestivalOccurrence>,
    val status: FestivalDataStatus,
    val errors: List<String>
) {
    val hasValidOccurrenceData: Boolean
        get() = status == FestivalDataStatus.AVAILABLE && occurrences.isNotEmpty()

    val availableYears: List<Int>
        get() = occurrences.map { it.year }.distinct().sorted()

    private val definitionsById: Map<String, FestivalDefinition> =
        definitions.associateBy { it.id }

    fun itemsForYear(year: Int, query: String = ""): List<FestivalCalendarItem> {
        val normalizedQuery = query.trim()
        if (!hasValidOccurrenceData) return emptyList()

        return occurrences
            .filter { it.year == year }
            .mapNotNull { occurrence ->
                definitionsById[occurrence.festivalId]?.let { definition ->
                    FestivalCalendarItem(
                        definition = definition,
                        occurrence = occurrence,
                        daysUntil = daysUntil(occurrence.date)
                    )
                }
            }
            .filter { item ->
                normalizedQuery.isBlank() ||
                    item.definition.name.contains(normalizedQuery, ignoreCase = true) ||
                    item.definition.id.contains(normalizedQuery, ignoreCase = true) ||
                    item.definition.hinduDateLabel.contains(
                        normalizedQuery,
                        ignoreCase = true
                    )
            }
            .sortedBy { it.occurrence?.date }
    }

    fun groupedItemsForYear(year: Int, query: String = ""): List<FestivalMonthGroup> {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        return itemsForYear(year, query)
            .groupBy { YearMonth.from(it.occurrence!!.date) }
            .toSortedMap()
            .map { (month, items) ->
                FestivalMonthGroup(title = month.format(formatter), items = items)
            }
    }

    fun upcomingItems(
        fromDate: LocalDate = LocalDate.now(),
        days: Long = 400L
    ): List<FestivalCalendarItem> {
        if (!hasValidOccurrenceData) return emptyList()
        val endDate = fromDate.plusDays(days)
        return occurrences
            .filter { !it.date.isBefore(fromDate) && !it.date.isAfter(endDate) }
            .mapNotNull { occurrence ->
                definitionsById[occurrence.festivalId]?.let { definition ->
                    FestivalCalendarItem(
                        definition = definition,
                        occurrence = occurrence,
                        daysUntil = daysUntil(occurrence.date, fromDate)
                    )
                }
            }
            .sortedBy { it.occurrence?.date }
    }

    fun findOccurrence(festivalId: String, date: LocalDate): FestivalCalendarItem? {
        val occurrence = occurrences.firstOrNull {
            it.festivalId == festivalId && it.date == date
        } ?: return null
        val definition = definitionsById[festivalId] ?: return null
        return FestivalCalendarItem(
            definition = definition,
            occurrence = occurrence,
            daysUntil = daysUntil(date)
        )
    }

    private fun daysUntil(
        date: LocalDate,
        fromDate: LocalDate = LocalDate.now()
    ): Long {
        return java.time.temporal.ChronoUnit.DAYS.between(fromDate, date)
    }

    companion object {
        fun invalid(message: String): FestivalRepositoryState {
            return FestivalRepositoryState(
                definitions = emptyList(),
                occurrences = emptyList(),
                status = FestivalDataStatus.INVALID,
                errors = listOf(message)
            )
        }
    }
}
