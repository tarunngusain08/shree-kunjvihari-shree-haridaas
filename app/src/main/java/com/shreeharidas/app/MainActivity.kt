package com.shreeharidas.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shreeharidas.app.festival.notification.FestivalNotificationScheduler
import com.shreeharidas.app.navigation.AppNavigation
import com.shreeharidas.app.ui.theme.ShreeHaridasTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Single activity that hosts the Compose navigation graph.
 * Requests essential permissions upfront on launch.
 */
class MainActivity : ComponentActivity() {
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestEssentialPermissions()
        resyncFestivalNotifications()
        setContent {
            ShreeHaridasTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        startupScope.cancel()
    }

    /**
     * Request DND override and battery optimization exemption upfront
     * so the app can reliably deliver reminders from the start.
     */
    private fun requestEssentialPermissions() {
        // Request DND override (ACCESS_NOTIFICATION_POLICY)
        val notificationManager = getSystemService(
            NotificationManager::class.java
        )
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            )
        }

        // Request battery optimization exemption
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            @Suppress("BatteryLife")
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun resyncFestivalNotifications() {
        startupScope.launch {
            FestivalNotificationScheduler(applicationContext)
                .resyncUpcomingNotifications()
        }
    }
}
