package com.shreeharidas.app.festival.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shreeharidas.app.MainActivity
import com.shreeharidas.app.R
import com.shreeharidas.app.data.PreferencesRepository
import com.shreeharidas.app.festival.data.FestivalRepository
import com.shreeharidas.app.util.Constants
import com.shreeharidas.app.util.PermissionUtils
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FestivalNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FestivalReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Constants.ACTION_FESTIVAL_NOTIFICATION) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleNotification(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Festival notification failed safely", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleNotification(context: Context, intent: Intent) {
        val key = intent.getStringExtra(
            FestivalNotificationScheduler.EXTRA_SCHEDULE_KEY
        ) ?: return
        val festivalId = intent.getStringExtra(
            FestivalNotificationScheduler.EXTRA_FESTIVAL_ID
        ) ?: return
        val occurrenceDate = intent.getStringExtra(
            FestivalNotificationScheduler.EXTRA_OCCURRENCE_DATE
        )?.let { LocalDate.parse(it) } ?: return
        val offsetDays = intent.getIntExtra(
            FestivalNotificationScheduler.EXTRA_OFFSET_DAYS,
            -1
        )
        if (offsetDays !in FestivalAlertPlanner.OFFSETS_DAYS) return

        val preferencesRepository = PreferencesRepository(context)
        val prefs = preferencesRepository.getFestivalPreferences()
        if (!prefs.notificationsEnabled || key in prefs.firedAlertKeys ||
            !PermissionUtils.hasNotificationPermission(context)
        ) {
            return
        }

        val state = FestivalRepository(context).loadState()
        val item = state.findOccurrence(festivalId, occurrenceDate) ?: return

        preferencesRepository.markFestivalAlertFired(key)
        showNotification(
            context = context,
            key = key,
            festivalName = item.definition.name,
            offsetDays = offsetDays
        )

        FestivalNotificationScheduler(context).resyncUpcomingNotifications()
    }

    private fun showNotification(
        context: Context,
        key: String,
        festivalName: String,
        offsetDays: Int
    ) {
        val contentIntent = PendingIntent.getActivity(
            context,
            FestivalNotificationScheduler.requestCodeForKey("open:$key"),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val body = if (offsetDays == 0) {
            context.getString(R.string.notification_festival_today_body, festivalName)
        } else {
            context.getString(
                R.string.notification_festival_upcoming_body,
                festivalName,
                offsetDays
            )
        }

        val notification = NotificationCompat.Builder(
            context,
            Constants.CHANNEL_ID_FESTIVALS
        )
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notification_festival_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationManager = context.getSystemService(
            NotificationManager::class.java
        )
        notificationManager.notify(
            FestivalNotificationScheduler.notificationIdForKey(key),
            notification
        )
    }
}
