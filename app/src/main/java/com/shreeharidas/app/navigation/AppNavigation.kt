package com.shreeharidas.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shreeharidas.app.ui.festival.FestivalScreen
import com.shreeharidas.app.ui.main.MainScreen

/**
 * App-level navigation.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.Reminders.route
    ) {
        composable(AppRoute.Reminders.route) {
            MainScreen(
                onFestivalCalendarClick = {
                    navController.navigate(AppRoute.Festivals.route)
                }
            )
        }
        composable(AppRoute.Festivals.route) {
            FestivalScreen(onBack = { navController.popBackStack() })
        }
    }
}

private sealed class AppRoute(val route: String) {
    data object Reminders : AppRoute("reminders")
    data object Festivals : AppRoute("festival_calendar")
}
