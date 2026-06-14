package com.shreeharidas.app.festival.data

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalAssetParserTest {

    @Test
    fun parseDefinitionsAcceptsCanonicalSchema() {
        val json = """
            {
              "schemaVersion": 1,
              "festivals": [
                {
                  "id": "RADHASHTAMI",
                  "name": "श्रीराधाष्टमी महोत्सव",
                  "month": "BHADRAPAD",
                  "paksha": "SHUKLA",
                  "tithi": 8,
                  "specialType": null
                }
              ]
            }
        """.trimIndent()

        val result = FestivalAssetParser.parseDefinitions(json)

        assertTrue(result is FestivalParseResult.Success)
        val definitions = (result as FestivalParseResult.Success).value
        assertEquals("RADHASHTAMI", definitions.single().id)
        assertEquals(8, definitions.single().tithi)
    }

    @Test
    fun parseDefinitionsRejectsUnsupportedSchema() {
        val json = """{"schemaVersion": 99, "festivals": []}"""

        val result = FestivalAssetParser.parseDefinitions(json)

        assertTrue(result is FestivalParseResult.Error)
    }

    @Test
    fun parseOccurrencesRejectsUnknownFestivalId() {
        val json = """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-06-13T00:00:00Z",
              "source": "Drik Panchang Vrindavan",
              "occurrences": [
                { "festivalId": "UNKNOWN", "date": "2026-09-20" }
              ]
            }
        """.trimIndent()

        val result = FestivalAssetParser.parseOccurrences(
            json = json,
            knownFestivalIds = setOf("RADHASHTAMI")
        )

        assertTrue(result is FestivalParseResult.Error)
    }

    @Test
    fun parseOccurrencesRejectsDuplicates() {
        val json = """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-06-13T00:00:00Z",
              "source": "Drik Panchang Vrindavan",
              "occurrences": [
                { "festivalId": "RADHASHTAMI", "date": "2026-09-20" },
                { "festivalId": "RADHASHTAMI", "date": "2026-09-20" }
              ]
            }
        """.trimIndent()

        val result = FestivalAssetParser.parseOccurrences(
            json = json,
            knownFestivalIds = setOf("RADHASHTAMI")
        )

        assertTrue(result is FestivalParseResult.Error)
    }

    @Test
    fun contiguousYearValidationAllowsAdjacentYears() {
        val error = FestivalAssetParser.validateContiguousYears(
            listOf(2026, 2027, 2028)
        )

        assertEquals(null, error)
    }

    @Test
    fun contiguousYearValidationRejectsGaps() {
        val error = FestivalAssetParser.validateContiguousYears(
            listOf(2026, 2028)
        )

        assertTrue(error != null)
    }

    @Test
    fun bundledYearWiseOccurrenceAssetsAreVerifiedAndContiguous() {
        val assets = mainAssetsPath()
        val definitionsJson = readUtf8(assets.resolve("festival_definitions.json"))
        val definitionResult = FestivalAssetParser.parseDefinitions(definitionsJson)
        assertTrue(definitionResult is FestivalParseResult.Success)
        val definitions = (definitionResult as FestivalParseResult.Success)
            .value
            .associateBy { it.id }

        val occurrenceFiles = Files.list(assets.resolve("festival_occurrences")).use { stream ->
            stream.iterator().asSequence()
                .filter { path -> path.fileName.toString().matches(Regex("\\d{4}\\.json")) }
                .sortedBy { path -> path.fileName.toString() }
                .toList()
        }
        val years = occurrenceFiles.map { path ->
            path.fileName.toString().removeSuffix(".json").toInt()
        }
        assertEquals((2026..2031).toList(), years)
        assertEquals(null, FestivalAssetParser.validateContiguousYears(years))

        val seen = mutableSetOf<String>()
        occurrenceFiles.forEach { path ->
            val root = JSONObject(readUtf8(path))
            assertEquals(FestivalAssetParser.SUPPORTED_SCHEMA_VERSION, root.getInt("schemaVersion"))
            val occurrences = root.getJSONArray("occurrences")
            assertTrue("${path.fileName} should contain verified rows", occurrences.length() > 0)

            for (index in 0 until occurrences.length()) {
                val item = occurrences.getJSONObject(index)
                val festivalId = item.getString("festivalId")
                val definition = definitions.getValue(festivalId)
                val occurrenceDate = LocalDate.parse(item.getString("date"))
                val key = "$festivalId:$occurrenceDate"

                assertTrue("Duplicate occurrence $key", seen.add(key))
                assertEquals("Drik Panchang Vrindavan", item.getString("source"))
                assertEquals("Vrindavan", item.getString("location"))
                assertTrue("Occurrence must be verified", item.getBoolean("verified"))
                assertTrue(
                    "Occurrence must cite Drik Panchang Vrindavan source",
                    item.getString("sourceUrl").contains("drikpanchang.com") &&
                        item.getString("sourceUrl").contains("geoname-id=1253079")
                )
                assertEquals(definition.month.name, item.getString("verifiedMonth"))
                assertEquals(definition.paksha?.name, item.getString("verifiedPaksha"))
                assertEquals(definition.tithi, item.getInt("verifiedTithi"))
            }
        }
    }

    private fun mainAssetsPath(): Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val candidates = listOf(
            userDir.resolve("src/main/assets"),
            userDir.resolve("app/src/main/assets")
        )
        return candidates.firstOrNull { Files.exists(it) }
            ?: error("Cannot find app main assets from $userDir")
    }

    private fun readUtf8(path: Path): String {
        return String(Files.readAllBytes(path), Charsets.UTF_8)
    }
}
