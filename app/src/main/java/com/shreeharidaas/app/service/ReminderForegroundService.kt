package com.shreeharidaas.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.shreeharidaas.app.R
import com.shreeharidaas.app.audio.SoundPlayer
import com.shreeharidaas.app.data.PreferencesRepository
import com.shreeharidaas.app.data.SoundRepository
import com.shreeharidaas.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Central coordinator foreground service for the alarm lifecycle.
 * Handles ACTION_START, ACTION_ALARM_TRIGGERED, and ACTION_STOP.
 */
class ReminderForegroundService : Service() {

    companion object {
        private const val TAG = "ReminderFgService"

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
     * ACTION_ALARM_TRIGGERED: Show notification, play sound, vibrate, reschedule.
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
                    // Fallback to default bell
                    Log.w(TAG, "Sound not accessible: $soundUri, using fallback")
                    val fallbackUri = soundRepository.getDefaultBellUri()
                    soundPlayer.play(fallbackUri)
                    preferencesRepository.setSoundUri(fallbackUri)
                    notificationHelper.showReminderNotification(
                        getString(R.string.notification_fallback_body)
                    )
                }

                // Vibrate if enabled
                if (prefs.vibrationEnabled) {
                    triggerVibration()
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

    /**
     * Trigger a short vibration pattern.
     */
    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val effect = VibrationEffect.createOneShot(
            300L,
            VibrationEffect.DEFAULT_AMPLITUDE
        )
        vibrator.vibrate(effect)
    }
}
