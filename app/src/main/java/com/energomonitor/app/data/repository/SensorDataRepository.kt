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
                
                // Filter out sensors with specific units
                if (unit == "#" || 
                    unit.equals("CZK", ignoreCase = true) ||
                    unit.equals("Wh", ignoreCase = true) ||
                    unit.equals("m3", ignoreCase = true) || 
                    unit.equals("m³", ignoreCase = true)) continue
                
                val medium = config?.medium ?: ""
                
                // Only process streams that match the requested topic
                val topic = determineTopic(title, unit, medium)
                if (topic != targetTopic) continue

                // 3. For each matching stream, fetch the latest data point
                try {
                    val dataPoints = apiService.getStreamData(feed.id, stream.id, limit = 1)
                    if (dataPoints.isNotEmpty() && dataPoints.first().size == 2) {
                        val timestamp = (dataPoints.first()[0] as Number).toLong()
                        val value = (dataPoints.first()[1] as Number).toDouble()
                        var finalValue = value
                        var finalUnit = unit

                        // Convert W > 1000 to kW
                        if (unit.equals("W", ignoreCase = true) && value >= 1000) {
                            finalValue = value / 1000.0
                            finalUnit = "kW"
                        }
                        
                        // Round value to 2 decimal places
                        val roundedValue = Math.round(finalValue * 100.0) / 100.0

                        allSensors.add(
                            SensorData(
                                id = stream.id,
                                feedId = feed.id,
                                title = title,
                                currentValue = roundedValue,
                                unit = finalUnit,
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

    private fun determineTopic(title: String, unit: String, medium: String): SensorTopic? {
        val lowerTitle = title.lowercase()
        val lowerUnit = unit.lowercase()
        val lowerMedium = medium.lowercase()

        // 1. Direct Medium Mapping (Prioritized if medium field is present)
        if (lowerMedium.contains("gas")) return SensorTopic.GAS
        if (lowerMedium.contains("water")) return SensorTopic.WATER
        if (lowerMedium.contains("co2") || lowerMedium.contains("carbon")) return SensorTopic.CO2
        if (lowerMedium.contains("temperature")) return SensorTopic.TEMPERATURE
        if (lowerMedium.contains("humidity")) return SensorTopic.HUMIDITY
        
        // Handling Electricity/Power medium logic and splitting by Unit
        if (lowerMedium.contains("electricity") || lowerMedium.contains("power")) {
            return if (lowerUnit.contains("wh")) {
                null // mapped out from energy
            } else if (lowerUnit.contains("w") && !lowerUnit.contains("wh")) {
                SensorTopic.POWER // kW, W
            } else {
                SensorTopic.POWER // Fallback
            }
        }

        // 2. Fallbacks based on unit and title heuristics (Legacy behavior)
        return when {
            lowerUnit.contains("°c") || lowerUnit.contains("celsius") || lowerTitle.contains("temp") -> SensorTopic.TEMPERATURE
            lowerUnit.contains("wh") -> null 
            lowerUnit.contains("w") -> SensorTopic.POWER 
            lowerTitle.contains("gas") -> SensorTopic.GAS
            lowerTitle.contains("water") -> SensorTopic.WATER
            lowerTitle.contains("co2") || lowerTitle.contains("carbon") -> SensorTopic.CO2
            lowerTitle.contains("electric") || lowerTitle.contains("power") -> SensorTopic.POWER
            lowerUnit.contains("%") || lowerTitle.contains("humid") -> SensorTopic.HUMIDITY
            else -> null
        }
    }

    suspend fun fetchHistoricalData(feedId: String, streamId: String, rangeMs: Long): List<Pair<Long, Double>> {
        val currentTimeInSeconds = System.currentTimeMillis() / 1000
        val timeFromInSeconds = currentTimeInSeconds - (rangeMs / 1000)
        
        return try {
            val dataPoints = apiService.getStreamData(
                feedId = feedId, 
                streamId = streamId, 
                limit = 1000000, 
                timeFrom = timeFromInSeconds
            )
            
            // Downsample to max ~500 points to prevent OOM and Canvas lag
            val maxPoints = 500
            val step = if (dataPoints.size > maxPoints) dataPoints.size / maxPoints else 1
            
            dataPoints.filterIndexed { index, _ -> index % step == 0 }.mapNotNull { point ->
                if (point.size == 2) {
                    val timestamp = (point[0] as Number).toLong()
                    val value = (point[1] as Number).toDouble()
                    Pair(timestamp, value)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
