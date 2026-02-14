package com.shreeharidas.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shreeharidas.app.service.ReminderForegroundService

/**
 * Thin relay receiver for scheduled alarms.
 * Delegates all work to ReminderForegroundService to avoid
 * the 10-second BroadcastReceiver ANR timeout.
 */
class AlarmBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmBroadcastReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Alarm received, delegating to ForegroundService")
        ReminderForegroundService.triggerAlarm(context)
    }
}
