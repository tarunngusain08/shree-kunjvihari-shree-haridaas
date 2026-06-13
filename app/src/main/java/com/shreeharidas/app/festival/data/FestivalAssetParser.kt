package com.shreeharidas.app.festival.data

import com.shreeharidas.app.festival.FestivalDefinition
import com.shreeharidas.app.festival.FestivalOccurrence
import com.shreeharidas.app.festival.FestivalSpecialType
import com.shreeharidas.app.festival.HinduMonth
import com.shreeharidas.app.festival.Paksha
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

sealed class FestivalParseResult<out T> {
    data class Success<T>(val value: T) : FestivalParseResult<T>()
    data class Error(val message: String) : FestivalParseResult<Nothing>()
}

object FestivalAssetParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    fun parseDefinitions(json: String): FestivalParseResult<List<FestivalDefinition>> {
        return try {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", -1)
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                return FestivalParseResult.Error(
                    "Unsupported festival definition schema version: $schemaVersion"
                )
            }

            val festivals = root.getJSONArray("festivals")
            val definitions = mutableListOf<FestivalDefinition>()
            val ids = mutableSetOf<String>()

            for (index in 0 until festivals.length()) {
                val item = festivals.getJSONObject(index)
                val id = item.getString("id")
                if (!ids.add(id)) {
                    return FestivalParseResult.Error("Duplicate festival id: $id")
                }

                definitions += FestivalDefinition(
                    id = id,
                    name = item.getString("name"),
                    month = enumValue(item.getString("month")),
                    paksha = item.optNullableString("paksha")?.let { enumValue<Paksha>(it) },
                    tithi = if (item.isNull("tithi")) null else item.getInt("tithi"),
                    specialType = item.optNullableString("specialType")
                        ?.let { enumValue<FestivalSpecialType>(it) }
                )
            }

            FestivalParseResult.Success(definitions.sortedWith(
                compareBy<FestivalDefinition> { it.month.order }
                    .thenBy { it.paksha?.ordinal ?: -1 }
                    .thenBy { it.tithi ?: 0 }
                    .thenBy { it.name }
            ))
        } catch (e: Exception) {
            FestivalParseResult.Error("Invalid festival definitions: ${e.message}")
        }
    }

    fun parseOccurrences(
        json: String,
        knownFestivalIds: Set<String>
    ): FestivalParseResult<List<FestivalOccurrence>> {
        return try {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", -1)
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                return FestivalParseResult.Error(
                    "Unsupported festival occurrence schema version: $schemaVersion"
                )
            }

            val source = root.optString("source", "")
            val generatedAt = root.optString("generatedAt", "")
            val occurrences = root.optJSONArray("occurrences") ?: JSONArray()
            val parsed = mutableListOf<FestivalOccurrence>()
            val keys = mutableSetOf<String>()

            for (index in 0 until occurrences.length()) {
                val item = occurrences.getJSONObject(index)
                val festivalId = item.getString("festivalId")
                if (festivalId !in knownFestivalIds) {
                    return FestivalParseResult.Error(
                        "Unknown festival id in occurrences: $festivalId"
                    )
                }

                val date = LocalDate.parse(item.getString("date"))
                val key = "$festivalId:${date}"
                if (!keys.add(key)) {
                    return FestivalParseResult.Error(
                        "Duplicate festival occurrence: $key"
                    )
                }

                parsed += FestivalOccurrence(
                    festivalId = festivalId,
                    date = date,
                    source = item.optString("source", source),
                    generatedAt = item.optString("generatedAt", generatedAt)
                )
            }

            FestivalParseResult.Success(parsed.sortedBy { it.date })
        } catch (e: Exception) {
            FestivalParseResult.Error("Invalid festival occurrences: ${e.message}")
        }
    }

    fun validateContiguousYears(years: List<Int>): String? {
        if (years.isEmpty()) return null
        val sorted = years.distinct().sorted()
        for (index in 1 until sorted.size) {
            if (sorted[index] != sorted[index - 1] + 1) {
                return "Year-wise occurrence files are not contiguous: $sorted"
            }
        }
        return null
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T {
        return enumValueOf(value.trim())
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = optString(name).trim()
        return value.ifEmpty { null }
    }
}
