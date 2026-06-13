package com.shreeharidas.app.festival.notification

import com.shreeharidas.app.festival.FestivalCalendarItem
import com.shreeharidas.app.festival.FestivalDefinition
import com.shreeharidas.app.festival.FestivalOccurrence
import com.shreeharidas.app.festival.HinduMonth
import com.shreeharidas.app.festival.Paksha
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FestivalAlertPlannerTest {

    @Test
    fun buildsAllOffsetsIncludingFestivalDay() {
        val alerts = FestivalAlertPlanner.buildAlerts(
            items = listOf(item(LocalDate.of(2026, 9, 30))),
            now = Instant.parse("2026-08-01T00:00:00Z"),
            zoneId = ZoneId.of("America/New_York")
        )

        assertEquals(listOf(30, 15, 7, 2, 1, 0), alerts.map { it.offsetDays })
    }

    @Test
    fun skipsPastAlertsAndFiredKeys() {
        val firedKey = FestivalAlertPlanner.scheduleKey(
            festivalId = "RADHASHTAMI",
            occurrenceDate = LocalDate.of(2026, 9, 30),
            offsetDays = 15
        )
        val alerts = FestivalAlertPlanner.buildAlerts(
            items = listOf(item(LocalDate.of(2026, 9, 30))),
            now = Instant.parse("2026-09-20T13:00:00Z"),
            zoneId = ZoneId.of("America/New_York"),
            firedKeys = setOf(firedKey)
        )

        assertFalse(alerts.any { it.offsetDays == 30 })
        assertFalse(alerts.any { it.offsetDays == 15 })
        assertTrue(alerts.any { it.offsetDays == 7 })
    }

    @Test
    fun schedulesAtEightAmDeviceLocalTime() {
        val alerts = FestivalAlertPlanner.buildAlerts(
            items = listOf(item(LocalDate.of(2026, 9, 30))),
            now = Instant.parse("2026-08-01T00:00:00Z"),
            zoneId = ZoneId.of("America/New_York")
        )
        val festivalDayAlert = alerts.first { it.offsetDays == 0 }
        val localTime = Instant.ofEpochMilli(festivalDayAlert.triggerAtMillis)
            .atZone(ZoneId.of("America/New_York"))
            .toLocalTime()

        assertEquals(8, localTime.hour)
        assertEquals(0, localTime.minute)
    }

    private fun item(date: LocalDate): FestivalCalendarItem {
        val definition = FestivalDefinition(
            id = "RADHASHTAMI",
            name = "श्रीराधाष्टमी महोत्सव",
            month = HinduMonth.BHADRAPAD,
            paksha = Paksha.SHUKLA,
            tithi = 8,
            specialType = null
        )
        return FestivalCalendarItem(
            definition = definition,
            occurrence = FestivalOccurrence(
                festivalId = definition.id,
                date = date,
                source = "Drik Panchang Vrindavan",
                generatedAt = "2026-06-13T00:00:00Z"
            ),
            daysUntil = null
        )
    }
}
