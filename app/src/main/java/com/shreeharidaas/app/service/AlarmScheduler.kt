package com.shreeharidaas.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shreeharidaas.app.data.PreferencesRepository
import com.shreeharidaas.app.data.UserPreferences
import com.shreeharidaas.app.receiver.AlarmBroadcastReceiver
import com.shreeharidaas.app.util.Constants
import java.util.Calendar

/**
 * Schedules and cancels exact alarms using AlarmManager.
 * Uses RTC_WAKEUP so that next_trigger_time survives reboots.
 * Respects the active time window — defers alarms to window start if needed.
 */
class AlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AlarmScheduler"
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule the next exact alarm at [triggerAtMillis] (wall-clock epoch).
     * Also persists the trigger time to DataStore.
     */
    suspend fun scheduleAlarm(
        triggerAtMillis: Long,
        preferencesRepository: PreferencesRepository
    ) {
        // Persist trigger time for restore after kill/reboot
        preferencesRepository.setNextTriggerTime(triggerAtMillis)

        val pendingIntent = createPendingIntent()

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.d(TAG, "Alarm scheduled for $triggerAtMillis " +
                "(in ${(triggerAtMillis - System.currentTimeMillis()) / 1000}s)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm - permission denied", e)
        }
    }

    /**
     * Schedule the next alarm based on an interval from now,
     * respecting the active time window. If the next trigger falls
     * outside the window, it is deferred to the window start time.
     */
    suspend fun scheduleAlarmFromNow(
        intervalMinutes: Int,
        preferencesRepository: PreferencesRepository
    ) {
        val prefs = preferencesRepository.getPreferences()
        val intervalMs = intervalMinutes * 60L * 1000L
        var triggerAtMillis = System.currentTimeMillis() + intervalMs

        // Adjust for active time window if enabled
        if (prefs.activeWindowEnabled) {
            triggerAtMillis = adjustForTimeWindow(triggerAtMillis, prefs)
        }

        scheduleAlarm(triggerAtMillis, preferencesRepository)
    }

    /**
     * Check if the given wall-clock time falls within the active window.
     * If not, return the next window-start time (today or tomorrow).
     */
    private fun adjustForTimeWindow(
        triggerAtMillis: Long,
        prefs: UserPreferences
    ): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val triggerMinuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(Calendar.MINUTE)

        val startMin = prefs.startTotalMinutes
        val endMin = prefs.endTotalMinutes

        val insideWindow = if (startMin <= endMin) {
            // Normal window: e.g. 08:00 - 22:00
            triggerMinuteOfDay in startMin..endMin
        } else {
            // Overnight window: e.g. 22:00 - 06:00
            triggerMinuteOfDay >= startMin || triggerMinuteOfDay <= endMin
        }

        if (insideWindow) {
            return triggerAtMillis
        }

        // Defer to the next window start
        Log.d(TAG, "Trigger at ${triggerMinuteOfDay}min outside window " +
            "[$startMin-$endMin], deferring to window start")

        val deferred = Calendar.getInstance().apply {
            timeInMillis = triggerAtMillis
            set(Calendar.HOUR_OF_DAY, prefs.startHour)
            set(Calendar.MINUTE, prefs.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the deferred time is in the past, move to tomorrow
        if (deferred.timeInMillis <= System.currentTimeMillis()) {
            deferred.add(Calendar.DAY_OF_YEAR, 1)
        }

        return deferred.timeInMillis
    }

    /**
     * Check if the current time is within the active window.
     * Returns true if window is disabled (all day) or current time is inside.
     */
    fun isCurrentlyInWindow(prefs: UserPreferences): Boolean {
        if (!prefs.activeWindowEnabled) return true

        val now = Calendar.getInstance()
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMin = prefs.startTotalMinutes
        val endMin = prefs.endTotalMinutes

        return if (startMin <= endMin) {
            nowMin in startMin..endMin
        } else {
            nowMin >= startMin || nowMin <= endMin
        }
    }

    /**
     * Cancel any scheduled alarm.
     */
    fun cancelAlarm() {
        val pendingIntent = createPendingIntent()
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Alarm cancelled")
    }

    /**
     * Check if the app can schedule exact alarms (API 31+).
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Creates the PendingIntent targeting AlarmBroadcastReceiver.
     * Uses a fixed request code to prevent duplicate alarms.
     */
    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmBroadcastReceiver::class.java).apply {
            action = Constants.ACTION_ALARM_TRIGGERED
        }
        return PendingIntent.getBroadcast(
            context,
            Constants.ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
