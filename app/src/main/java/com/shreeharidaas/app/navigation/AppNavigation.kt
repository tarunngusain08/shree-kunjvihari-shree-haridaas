package com.shreeharidaas.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shreeharidaas.app.ui.main.MainScreen
import com.shreeharidaas.app.ui.settings.SettingsScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

/**
 * App-level navigation host with Main and Settings screens.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
