package com.shreeharidaas.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shreeharidaas.app.navigation.AppNavigation
import com.shreeharidaas.app.ui.theme.ShreeHaridaasTheme

/**
 * Single activity that hosts the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShreeHaridaasTheme {
                AppNavigation()
            }
        }
    }
}
