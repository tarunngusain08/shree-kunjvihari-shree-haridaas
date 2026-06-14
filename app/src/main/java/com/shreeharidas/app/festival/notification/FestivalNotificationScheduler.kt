package com.shreeharidas.app.festival.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.shreeharidas.app.data.PreferencesRepository
import com.shreeharidas.app.festival.FestivalCalendarItem
import com.shreeharidas.app.festival.data.FestivalRepository
import com.shreeharidas.app.util.Constants
import com.shreeharidas.app.util.PermissionUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FestivalAlert(
    val key: String,
    val festivalId: String,
    val festivalName: String,
    val occurrenceDate: LocalDate,
    val offsetDays: Int,
    val triggerAtMillis: Long
)

object FestivalAlertPlanner {
    val OFFSETS_DAYS = listOf(30, 15, 7, 2, 1, 0)
    private val FIRE_TIME = LocalTime.of(8, 0)

    fun buildAlerts(
        items: List<FestivalCalendarItem>,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        rollingWindowDays: Long = 400L,
        firedKeys: Set<String> = emptySet()
    ): List<FestivalAlert> {
        val windowEnd = now.plusSeconds(rollingWindowDays * 24L * 60L * 60L)

        return items.flatMap { item ->
            val occurrence = item.occurrence ?: return@flatMap emptyList()
            OFFSETS_DAYS.mapNotNull { offset ->
                val alertDate = occurrence.date.minusDays(offset.toLong())
                val triggerAt = alertDate.atTime(FIRE_TIME)
                    .atZone(zoneId)
                    .toInstant()
                val key = scheduleKey(
                    festivalId = item.definition.id,
                    occurrenceDate = occurrence.date,
                    offsetDays = offset
                )

                if (!triggerAt.isAfter(now) || triggerAt.isAfter(windowEnd) ||
                    key in firedKeys
                ) {
                    null
                } else {
                    FestivalAlert(
                        key = key,
                        festivalId = item.definition.id,
                        festivalName = item.definition.name,
                        occurrenceDate = occurrence.date,
                        offsetDays = offset,
                        triggerAtMillis = triggerAt.toEpochMilli()
                    )
                }
            }
        }.sortedBy { it.triggerAtMillis }
    }

    fun scheduleKey(
        festivalId: String,
        occurrenceDate: LocalDate,
        offsetDays: Int
    ): String = "$festivalId:$occurrenceDate:$offsetDays"
}

class FestivalNotificationScheduler(private val context: Context) {

    companion object {
        private const val TAG = "FestivalScheduler"
        const val EXTRA_SCHEDULE_KEY = "extra_schedule_key"
        const val EXTRA_FESTIVAL_ID = "extra_festival_id"
        const val EXTRA_FESTIVAL_NAME = "extra_festival_name"
        const val EXTRA_OCCURRENCE_DATE = "extra_occurrence_date"
        const val EXTRA_OFFSET_DAYS = "extra_offset_days"

        fun requestCodeForKey(key: String): Int {
            return Constants.FESTIVAL_REQUEST_CODE_BASE +
                stablePositiveHash(key) % Constants.FESTIVAL_REQUEST_CODE_SPAN
        }

        fun notificationIdForKey(key: String): Int {
            return Constants.NOTIFICATION_ID_FESTIVAL_BASE +
                stablePositiveHash("notification:$key") %
                Constants.FESTIVAL_REQUEST_CODE_SPAN
        }

        private fun stablePositiveHash(value: String): Int {
            var hash = 0
            for (char in value) {
                hash = 31 * hash + char.code
            }
            return hash and Int.MAX_VALUE
        }
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val preferencesRepository = PreferencesRepository(context)
    private val festivalRepository = FestivalRepository(context)

    suspend fun resyncUpcomingNotifications() = withContext(Dispatchers.IO) {
        runCatching {
            preferencesRepository.setFestivalLastSyncEpoch(System.currentTimeMillis())

            val prefs = preferencesRepository.getFestivalPreferences()
            val state = festivalRepository.loadState()

            if (!prefs.notificationsEnabled || !state.hasValidOccurrenceData ||
                !PermissionUtils.hasNotificationPermission(context) ||
                !canScheduleExactAlarms()
            ) {
                cancelStoredAlerts(prefs.scheduledAlertKeys)
                preferencesRepository.setFestivalScheduledAlertKeys(emptySet())
                return@withContext
            }

            val upcomingItems = state.upcomingItems(
                fromDate = LocalDate.now(),
                days = 400L
            )
            val alerts = FestivalAlertPlanner.buildAlerts(
                items = upcomingItems,
                firedKeys = prefs.firedAlertKeys
            )
            val desiredKeys = alerts.map { it.key }.toSet()

            cancelStoredAlerts(prefs.scheduledAlertKeys - desiredKeys)
            alerts.forEach { scheduleAlert(it) }

            preferencesRepository.setFestivalScheduledAlertKeys(desiredKeys)
            preferencesRepository.setFestivalLastScheduleEpoch(System.currentTimeMillis())
        }.onFailure {
            Log.w(TAG, "Festival notification resync failed safely", it)
        }
    }

    private fun scheduleAlert(alert: FestivalAlert) {
        val pendingIntent = createPendingIntent(alert.key, alert)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alert.triggerAtMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled festival alert ${alert.key}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot schedule festival alert ${alert.key}", e)
        }
    }

    private fun cancelStoredAlerts(keys: Set<String>) {
        keys.forEach { key ->
            val pendingIntent = createPendingIntent(key, null)
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun createPendingIntent(
        key: String,
        alert: FestivalAlert?
    ): PendingIntent {
        val intent = Intent(context, FestivalNotificationReceiver::class.java).apply {
            action = Constants.ACTION_FESTIVAL_NOTIFICATION
            data = Uri.Builder()
                .scheme("shreeharidas")
                .authority("festival-alert")
                .appendPath(key)
                .build()
            putExtra(EXTRA_SCHEDULE_KEY, key)
            alert?.let {
                putExtra(EXTRA_FESTIVAL_ID, it.festivalId)
                putExtra(EXTRA_FESTIVAL_NAME, it.festivalName)
                putExtra(EXTRA_OCCURRENCE_DATE, it.occurrenceDate.toString())
                putExtra(EXTRA_OFFSET_DAYS, it.offsetDays)
            }
        }

        return PendingIntent.getBroadcast(
            context,
            requestCodeForKey(key),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
