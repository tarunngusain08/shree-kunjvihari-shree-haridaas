package com.shreeharidas.app.festival.data

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
}
