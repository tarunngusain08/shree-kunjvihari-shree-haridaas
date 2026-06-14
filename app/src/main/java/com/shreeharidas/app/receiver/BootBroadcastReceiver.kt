package com.shreeharidas.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shreeharidas.app.data.PreferencesRepository
import com.shreeharidas.app.festival.notification.FestivalNotificationScheduler
import com.shreeharidas.app.service.AlarmScheduler
import com.shreeharidas.app.service.ReminderForegroundService
import kotlinx.coroutines.runBlocking

/**
 * Restores alarm scheduling after device reboot.
 * Reads persisted state and reschedules if a reminder was active.
 * Bonus feature beyond MVP (PRD marks as "optional v1.1").
 */
class BootBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed, checking for active reminder")

        // Blocking read is acceptable in boot receiver
        // (system gives generous time for BOOT_COMPLETED)
        val prefs = runBlocking {
            PreferencesRepository(context).getPreferences()
        }

        if (!prefs.isRunning) {
            Log.d(TAG, "No active reminder, nothing to restore")
        } else {
            Log.d(TAG, "Restoring active reminder (freq=${prefs.frequencyMinutes}min)")

            val now = System.currentTimeMillis()
            val nextTrigger = prefs.nextTriggerTime

            if (nextTrigger > now) {
                // Future trigger: schedule at the correct wall-clock time
                Log.d(TAG, "Scheduling future alarm at $nextTrigger")
                val alarmScheduler = AlarmScheduler(context)
                runBlocking {
                    alarmScheduler.scheduleAlarm(
                        nextTrigger,
                        PreferencesRepository(context)
                    )
                }
            } else {
                // Missed trigger: fire immediately via service
                Log.d(TAG, "Missed alarm trigger, firing immediately")
                ReminderForegroundService.triggerAlarm(context)
            }

            // Restart the foreground service for persistent notification
            ReminderForegroundService.startService(context)
        }

        runCatching {
            runBlocking {
                FestivalNotificationScheduler(context).resyncUpcomingNotifications()
            }
        }.onFailure {
            Log.w(TAG, "Festival notification restore failed safely", it)
        }
    }
}
