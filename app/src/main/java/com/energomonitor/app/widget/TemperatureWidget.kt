package com.energomonitor.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.energomonitor.app.data.repository.SensorDataRepository
import com.energomonitor.app.domain.model.SensorTopic
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Row

class TemperatureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun sensorDataRepository(): SensorDataRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val prefs = currentState<Preferences>()
                val feedId = prefs[feedIdKey]
                val streamId = prefs[streamIdKey]

                if (feedId == null || streamId == null) {
                    WidgetSetupView()
                } else {
                    val title = prefs[titleKey] ?: "Sensor"
                    val value = prefs[valueKey]
                    val timestamp = prefs[timestampKey] ?: 0L
                    val fontOffset = prefs[fontSizeKey] ?: 0
                    WidgetContentView(title, value, timestamp, fontOffset)
                }
            }
        }
    }

    suspend fun updateAll(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repository = entryPoint.sensorDataRepository()

        // In a real scenario we'd query glanceManager for all instances and update
        // their state using updateAppWidgetState.
        // For simplicity, we can just fetch data for the widget keys in datastore.
        
        // Let's implement this generically using GlanceAppWidgetManager
        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(TemperatureWidget::class.java)
        
        for (glanceId in glanceIds) {
            updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
                val feedId = prefs[feedIdKey]
                val streamId = prefs[streamIdKey]
                
                if (feedId != null && streamId != null) {
                    try {
                        // Directly query the API for the single stream needed.
                        // This prevents downloading the entire topic graph every 15 minutes per widget.
                        val dataPoints = repository.fetchHistoricalData(feedId, streamId, 1) // Just gives us latest 1 point efficiently
                        if (dataPoints.isNotEmpty()) {
                            val latestPoint = dataPoints.last()
                            prefs[valueKey] = latestPoint.second
                            prefs[timestampKey] = latestPoint.first
                        } else {
                           // Fallback to fetch whole topic if we need to get the title dynamically
                           val sensors = repository.fetchSensorDataForTopic(SensorTopic.TEMPERATURE)
                           val targetSensor = sensors.find { it.feedId == feedId && it.id == streamId }
                           if (targetSensor != null) {
                               prefs[titleKey] = targetSensor.title
                               prefs[valueKey] = targetSensor.currentValue
                               prefs[timestampKey] = targetSensor.timestamp
                           }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            update(context, glanceId)
        }
    }

    companion object {
        val feedIdKey = stringPreferencesKey("widget_feed_id")
        val streamIdKey = stringPreferencesKey("widget_stream_id")
        val titleKey = stringPreferencesKey("widget_title")
        val valueKey = doublePreferencesKey("widget_value")
        val timestampKey = longPreferencesKey("widget_timestamp")
        val fontSizeKey = intPreferencesKey("widget_font_size_offset")
    }
}

@Composable
fun WidgetSetupView() {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(Color.DarkGray)
            .padding(8.dp)
            .clickable(actionStartActivity(Intent().setClassName("com.energomonitor.app", "com.energomonitor.app.MainActivity"))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Setup Required",
            style = TextStyle(color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White))
        )
    }
}

@Composable
fun WidgetContentView(title: String, value: Double?, timestamp: Long, fontOffset: Int) {
    val displayTime = if (timestamp > 0) {
        val isMs = timestamp > 1000000000000L
        val date = if (isMs) Date(timestamp) else Date(timestamp * 1000)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else "--:--"

    // Check if the timestamp is older than 2 hours from now
    val isOutdated = if (timestamp > 0) {
        val isMs = timestamp > 1000000000000L
        val timestampMs = if (isMs) timestamp else timestamp * 1000
        val twoHoursInMillis = 2 * 60 * 60 * 1000L
        (System.currentTimeMillis() - timestampMs) > twoHoursInMillis
    } else false

    val backgroundColor = when {
        value == null || isOutdated -> Color.DarkGray
        value < 0 -> Color(0xFF00008B) // Dark Blue
        value in 0.0..10.0 -> Color(0xFF006400) // Dark Green
        value > 10.0 && value <= 20.0 -> Color(0xFFB8860B) // Dark Yellow
        value > 20.0 && value <= 30.0 -> Color(0xFFFF8C00) // Dark Orange
        else -> Color(0xFF8B0000) // Dark Red
    }

    val size = LocalSize.current
    val isLandscape = size.width > size.height
    // Many 1x1 widgets on 5-column grids evaluate slightly larger than 100.dp (often 110-115.dp)
    val isSmall = size.width <= 120.dp && size.height <= 120.dp

    val shortTitle = if (title.length > 20) {
        ".." + title.takeLast(18)
    } else {
        title
    }

    if (isSmall) {
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(backgroundColor)
                .padding(4.dp)
                .clickable(actionStartActivity(Intent().setClassName("com.energomonitor.app", "com.energomonitor.app.MainActivity"))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (value != null) "${value}°C" else "--",
                style = TextStyle(
                    color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                    fontSize = (28 + (fontOffset * 2)).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.glance.text.TextAlign.Center
                ),
                maxLines = 2
            )
        }
    } else if (isLandscape) {
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .background(backgroundColor)
                .clickable(actionStartActivity(Intent().setClassName("com.energomonitor.app", "com.energomonitor.app.MainActivity"))),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Half
            Column(
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.defaultWeight().padding(8.dp)
            ) {
                Text(
                    text = shortTitle,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = (12 + fontOffset).sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 3
                )
                Text(
                    text = displayTime,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = (11 + fontOffset).sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
            
            // Right Half
            androidx.glance.layout.Box(
                modifier = GlanceModifier.defaultWeight().padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (value != null) "${value}°C" else "--",
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = (24 + (fontOffset * 2)).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 2
                )
            }
        }
    } else {
        // Fallback for 1x2 (tall) or 2x2 (large square) widgets
        
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(backgroundColor)
                .clickable(actionStartActivity(Intent().setClassName("com.energomonitor.app", "com.energomonitor.app.MainActivity"))),
        ) {
            // Upper half: Title and Time
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = shortTitle,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = (12 + fontOffset).sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 3
                )
                Text(
                    text = displayTime,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = (11 + fontOffset).sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(top = 4.dp)
                )
            }
            // Lower half: Sensor Value
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (value != null) "${value}°C" else "--",
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = (24 + (fontOffset * 2)).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 2
                )
            }
        }
    }
}
