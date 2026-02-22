package com.energomonitor.app.data.repository

import com.energomonitor.app.data.local.UserPreferences
import com.energomonitor.app.data.remote.EnergomonitorApiService
import com.energomonitor.app.domain.model.SensorData
import com.energomonitor.app.domain.model.SensorTopic
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorDataRepository @Inject constructor(
    private val apiService: EnergomonitorApiService,
    private val userPreferences: UserPreferences
) {
    suspend fun fetchSensorDataForTopic(targetTopic: SensorTopic): List<SensorData> {
        val userId = userPreferences.userId.first() ?: throw IllegalStateException("User ID not found")
        
        // 1. Fetch Feeds
        val feeds = apiService.getFeeds(userId)
        val allSensors = mutableListOf<SensorData>()

        // 2. Fetch Streams for each Feed
        for (feed in feeds) {
            val streams = apiService.getStreams(feed.id)
            for (stream in streams) {
                val config = stream.configs?.firstOrNull()
                val title = config?.title ?: stream.id
                val unit = config?.unit ?: ""
                
                // Only process streams that match the requested topic
                val topic = determineTopic(title, unit)
                if (topic != targetTopic) continue

                // 3. For each matching stream, fetch the latest data point
                try {
                    val dataPoints = apiService.getStreamData(feed.id, stream.id, limit = 1)
                    if (dataPoints.isNotEmpty() && dataPoints.first().size == 2) {
                        val timestamp = (dataPoints.first()[0] as Number).toLong()
                        val value = (dataPoints.first()[1] as Number).toDouble()
                        
                        // Round value to 2 decimal places
                        val roundedValue = Math.round(value * 100.0) / 100.0

                        allSensors.add(
                            SensorData(
                                id = stream.id,
                                title = title,
                                currentValue = roundedValue,
                                unit = unit,
                                timestamp = timestamp
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Ignore individual stream errors to allow others to load
                    e.printStackTrace()
                }
            }
        }

        return allSensors
    }

    private fun determineTopic(title: String, unit: String): SensorTopic? {
        val lowerTitle = title.lowercase()
        val lowerUnit = unit.lowercase()

        return when {
            lowerUnit.contains("°c") || lowerUnit.contains("celsius") || lowerTitle.contains("temp") -> SensorTopic.TEMPERATURE
            lowerUnit.contains("w") || lowerTitle.contains("electric") || lowerTitle.contains("power") || lowerTitle.contains("gas") -> SensorTopic.ENERGY
            else -> null
        }
    }
}
