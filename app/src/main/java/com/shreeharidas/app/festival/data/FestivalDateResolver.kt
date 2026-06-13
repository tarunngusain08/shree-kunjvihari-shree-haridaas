package com.shreeharidas.app.festival.data

import com.shreeharidas.app.festival.FestivalCalendarItem
import com.shreeharidas.app.festival.FestivalMonthGroup
import java.time.LocalDate

class FestivalDateResolver(private val repository: FestivalRepository) {

    suspend fun upcoming(
        fromDate: LocalDate = LocalDate.now(),
        days: Long = 400L
    ): List<FestivalCalendarItem> {
        return repository.loadState().upcomingItems(fromDate, days)
    }

    suspend fun groupedYear(
        year: Int,
        query: String = ""
    ): List<FestivalMonthGroup> {
        return repository.loadState().groupedItemsForYear(year, query)
    }
}
