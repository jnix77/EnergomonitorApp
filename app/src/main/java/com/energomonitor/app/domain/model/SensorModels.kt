package com.energomonitor.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SensorData(
    val id: String,
    val title: String,
    val currentValue: Double,
    val unit: String,
    val timestamp: Long
)

enum class SensorTopic(val displayName: String) {
    TEMPERATURE("Temperature"),
    ENERGY("Energy")
}

data class GroupedSensorData(
    val topic: SensorTopic,
    val sensors: List<SensorData>
)
