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
    const val SENSOR_DETAIL = "sensor_detail/{topic}/{feedId}/{streamId}/{title}"
    const val TEMPERATURE_DETAIL = "temperature_detail/{feedId}/{streamId}/{title}"

    fun createTemperatureDetailRoute(feedId: String, streamId: String, title: String): String {
        return "temperature_detail/$feedId/$streamId/$title"
    }

    fun createSensorDetailRoute(topic: String, feedId: String, streamId: String, title: String): String {
        return "sensor_detail/$topic/$feedId/$streamId/$title"
    }
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
                },
                onNavigateToTemperatureDetail = { feedId, streamId, title ->
                    navController.navigate(Destinations.createTemperatureDetailRoute(feedId, streamId, java.net.URLEncoder.encode(title, "UTF-8")))
                },
                onNavigateToSensorDetail = { topic, feedId, streamId, title ->
                    navController.navigate(Destinations.createSensorDetailRoute(topic.name, feedId, streamId, java.net.URLEncoder.encode(title, "UTF-8")))
                }
            )
        }

        composable(
            route = Destinations.TEMPERATURE_DETAIL,
            arguments = listOf(
                androidx.navigation.navArgument("feedId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("streamId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val feedId = backStackEntry.arguments?.getString("feedId") ?: ""
            val streamId = backStackEntry.arguments?.getString("streamId") ?: ""
            val title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            
            com.energomonitor.app.ui.temperature.TemperatureDetailScreen(
                feedId = feedId,
                streamId = streamId,
                title = title,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Destinations.SENSOR_DETAIL,
            arguments = listOf(
                androidx.navigation.navArgument("topic") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("feedId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("streamId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val topicString = backStackEntry.arguments?.getString("topic") ?: ""
            val topic = try {
                com.energomonitor.app.domain.model.SensorTopic.valueOf(topicString)
            } catch (e: Exception) {
                com.energomonitor.app.domain.model.SensorTopic.ENERGY // Fallback
            }
            val feedId = backStackEntry.arguments?.getString("feedId") ?: ""
            val streamId = backStackEntry.arguments?.getString("streamId") ?: ""
            val title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            
            com.energomonitor.app.ui.detail.SensorDetailScreen(
                topic = topic,
                feedId = feedId,
                streamId = streamId,
                title = title,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
