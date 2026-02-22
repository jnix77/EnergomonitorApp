package com.energomonitor.app.domain.model

data class SensorData(
    val id: String,
    val title: String,
    val currentValue: Double,
    val unit: String,
    val timestamp: Long
)

enum class SensorTopic(val displayName: String) {
    TEMPERATURE("Temperature"),
    HUMIDITY("Humidity"),
    ELECTRICITY("Electricity"),
    WATER("Water Consumption"),
    GAS("Gas Consumption"),
    UNKNOWN("Other Sensors")
}

data class GroupedSensorData(
    val topic: SensorTopic,
    val sensors: List<SensorData>
)
