package com.energomonitor.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.energomonitor.app.ui.main.MainScreen
import com.energomonitor.app.ui.settings.SettingsScreen
import com.energomonitor.app.ui.settings.SettingsViewModel

object Destinations {
    const val SETTINGS = "settings"
    const val DASHBOARD = "dashboard"
}

@Composable
fun EnergomonitorAppContent() {
    val navController = rememberNavController()
    
    // We use SettingsViewModel to check if we are logged in
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val uiState by settingsViewModel.uiState.collectAsState()

    val startDestination = if (uiState.isLoggedIn) Destinations.DASHBOARD else Destinations.SETTINGS

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onNavigateToMain = {
                    navController.navigate(Destinations.DASHBOARD) {
                        popUpTo(Destinations.SETTINGS) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.DASHBOARD) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Destinations.SETTINGS)
                }
            )
        }
    }
}
