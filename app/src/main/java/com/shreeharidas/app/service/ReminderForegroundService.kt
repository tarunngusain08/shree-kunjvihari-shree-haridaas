package com.shreeharidas.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.shreeharidas.app.R
import com.shreeharidas.app.audio.SoundPlayer
import com.shreeharidas.app.data.PreferencesRepository
import com.shreeharidas.app.data.SoundRepository
import com.shreeharidas.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Central coordinator foreground service for the alarm lifecycle.
 * Handles ACTION_START, ACTION_ALARM_TRIGGERED, and ACTION_STOP.
 */
class ReminderForegroundService : Service() {

    companion object {
        private const val TAG = "ReminderFgService"
        private const val RESTART_REQUEST_CODE = 9999

        fun startService(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java).apply {
                action = Constants.ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java).apply {
                action = Constants.ACTION_STOP
            }
            context.startService(intent)
        }

        fun triggerAlarm(context: Context) {
            val intent = Intent(context, ReminderForegroundService::class.java).apply {
                action = Constants.ACTION_ALARM_TRIGGERED
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var soundRepository: SoundRepository
    private lateinit var soundPlayer: SoundPlayer
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        preferencesRepository = PreferencesRepository(applicationContext)
        soundRepository = SoundRepository(applicationContext)
        soundPlayer = SoundPlayer(applicationContext)
        alarmScheduler = AlarmScheduler(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START -> handleStart()
            Constants.ACTION_ALARM_TRIGGERED -> handleAlarmTriggered()
            Constants.ACTION_STOP -> handleStop()
            else -> {
                // Service restarted by system — restore state
                handleRestore()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()
        serviceScope.cancel()
    }

    /**
     * Called when the user swipes the app from recents.
     * Schedule a self-restart alarm to survive process death.
     * Also re-ensure the reminder alarm is registered.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed — scheduling service restart")

        // Use runBlocking here since onTaskRemoved runs synchronously
        // and the process will die shortly after this returns
        val prefs = runBlocking {
            preferencesRepository.getPreferences()
        }

        if (!prefs.isRunning) {
            Log.d(TAG, "Not running, no need to restart")
            return
        }

        // 1) Re-register the reminder alarm (some OEMs cancel alarms on swipe)
        if (prefs.nextTriggerTime > System.currentTimeMillis()) {
            runBlocking {
                alarmScheduler.scheduleAlarm(
                    prefs.nextTriggerTime,
                    preferencesRepository
                )
            }
        }

        // 2) Schedule a self-restart in 1 second as backup
        val restartIntent = Intent(applicationContext, ReminderForegroundService::class.java)
        val pendingIntent = PendingIntent.getForegroundService(
            applicationContext,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            alarmMgr.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000L,
                pendingIntent
            )
            Log.d(TAG, "Restart alarm scheduled")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule restart alarm", e)
        }
    }

    /**
     * ACTION_START: Start foreground and schedule the first alarm.
     */
    private fun handleStart() {
        Log.d(TAG, "Handling ACTION_START")
        startForegroundWithNotification()

        serviceScope.launch {
            val prefs = preferencesRepository.getPreferences()
            alarmScheduler.scheduleAlarmFromNow(
                prefs.frequencyMinutes,
                preferencesRepository
            )
        }
    }

    /**
     * ACTION_ALARM_TRIGGERED: Show notification, play sound, reschedule.
     * If outside the active time window, silently reschedule to window start.
     */
    private fun handleAlarmTriggered() {
        Log.d(TAG, "Handling ACTION_ALARM_TRIGGERED")
        // Ensure we're in foreground
        startForegroundWithNotification()

        serviceScope.launch {
            val prefs = preferencesRepository.getPreferences()

            // Check if still supposed to be running
            if (!prefs.isRunning) {
                Log.w(TAG, "Alarm triggered but not running, ignoring")
                return@launch
            }

            // Check if we're inside the active time window
            val inWindow = alarmScheduler.isCurrentlyInWindow(prefs)

            if (inWindow) {
                // Play sound and show notification
                val soundUri = prefs.soundUri
                val isAccessible = soundRepository.isUriAccessible(soundUri)

                if (isAccessible) {
                    soundPlayer.play(soundUri)
                    notificationHelper.showReminderNotification()
                } else {
                    // Fallback to default sound
                    Log.w(TAG, "Sound not accessible: $soundUri, using fallback")
                    val fallbackUri = soundRepository.getDefaultSoundUri()
                    soundPlayer.play(fallbackUri)
                    preferencesRepository.setSoundUri(fallbackUri)
                    notificationHelper.showReminderNotification(
                        getString(R.string.notification_fallback_body)
                    )
                }
            } else {
                Log.d(TAG, "Outside active time window, skipping sound/notification")
            }

            // Always reschedule (adjustForTimeWindow handles deferral)
            alarmScheduler.scheduleAlarmFromNow(
                prefs.frequencyMinutes,
                preferencesRepository
            )
        }
    }

    /**
     * ACTION_STOP: Cancel alarm, clear state, stop service.
     * IMPORTANT: Must clear running state BEFORE calling stopSelf(),
     * otherwise START_STICKY will restart the service and handleRestore()
     * will see isRunning=true and resume notifications.
     */
    private fun handleStop() {
        Log.d(TAG, "Handling ACTION_STOP")
        alarmScheduler.cancelAlarm()
        soundPlayer.stop()

        // Cancel any visible reminder notification in the drawer
        notificationHelper.cancelReminderNotification()

        serviceScope.launch {
            // Clear state first, then stop the service
            preferencesRepository.clearRunningState()
            Log.d(TAG, "Running state cleared, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * System restart: check if we should be running and restore.
     */
    private fun handleRestore() {
        Log.d(TAG, "Handling restore")
        startForegroundWithNotification()

        serviceScope.launch {
            val prefs = preferencesRepository.getPreferences()
            if (prefs.isRunning) {
                val now = System.currentTimeMillis()
                val nextTrigger = prefs.nextTriggerTime

                if (nextTrigger > now) {
                    // Future trigger: schedule at correct time
                    alarmScheduler.scheduleAlarm(
                        nextTrigger,
                        preferencesRepository
                    )
                } else {
                    // Missed trigger: fire immediately, then schedule next
                    handleAlarmTriggered()
                }
            } else {
                // Not supposed to be running, stop self
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /**
     * Start foreground mode with the persistent service notification.
     */
    private fun startForegroundWithNotification() {
        val notification = notificationHelper.buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID_SERVICE, notification)
        }
    }

}
