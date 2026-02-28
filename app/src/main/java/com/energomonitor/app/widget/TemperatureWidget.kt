package com.energomonitor.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
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
import com.energomonitor.app.MainActivity
import com.energomonitor.app.data.repository.SensorDataRepository
import com.energomonitor.app.domain.model.SensorTopic
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withContext
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
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val repository = entryPoint.sensorDataRepository()
        
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
                    WidgetContentView(title, value, timestamp)
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
                        // Fetch temperature sensors
                        val sensors = repository.fetchSensorDataForTopic(SensorTopic.TEMPERATURE)
                        val targetSensor = sensors.find { it.feedId == feedId && it.id == streamId }
                        
                        if (targetSensor != null) {
                            prefs[titleKey] = targetSensor.title
                            prefs[valueKey] = targetSensor.currentValue
                            prefs[timestampKey] = targetSensor.timestamp
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
fun WidgetContentView(title: String, value: Double?, timestamp: Long) {
    val backgroundColor = when {
        value == null -> Color.DarkGray
        value < 0 -> Color(0xFF00008B) // Dark Blue
        value in 0.0..10.0 -> Color(0xFF006400) // Dark Green
        value > 10.0 && value <= 20.0 -> Color(0xFFB8860B) // Dark Yellow
        value > 20.0 && value <= 30.0 -> Color(0xFFFF8C00) // Dark Orange
        else -> Color(0xFF8B0000) // Dark Red
    }
    
    val timeString = if (timestamp > 0) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp * 1000L)) // API timestamps are in seconds usually, need to check DB
    } else {
        "--:--"
    }

    // Checking if the original timestamp was in ms or sec. Our repository:
    // val timestamp = (dataPoints.first()[0] as Number).toLong() (which is in ms usually for Energomonitor)
    val displayTime = if (timestamp > 0) {
        val isMs = timestamp > 1000000000000L
        val date = if (isMs) Date(timestamp) else Date(timestamp * 1000)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else "--:--"

    val size = LocalSize.current
    val isLandscape = size.width > size.height
    // Many 1x1 widgets on 5-column grids evaluate slightly larger than 100.dp (often 110-115.dp)
    val isSmall = size.width <= 120.dp && size.height <= 120.dp

    val shortTitle = if (title.contains(" ") && title.length > 10) {
        title.substringAfter(" ")
    } else if (title.length > 15) {
        ".." + title.takeLast(13)
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
                text = displayTime,
                style = TextStyle(
                    color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 12.sp,
                    textAlign = androidx.glance.text.TextAlign.Center
                ),
                maxLines = 1,
            )
            Text(
                text = if (value != null) "${value}°C" else "--",
                style = TextStyle(
                    color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.glance.text.TextAlign.Center
                ),
                maxLines = 2,
                modifier = GlanceModifier.padding(top = 4.dp)
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
                        fontSize = 12.sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 3
                )
                Text(
                    text = displayTime,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 11.sp,
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
                        fontSize = 24.sp,
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
                        fontSize = 12.sp,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 3
                )
                Text(
                    text = displayTime,
                    style = TextStyle(
                        color = androidx.glance.color.ColorProvider(day = Color.White, night = Color.White),
                        fontSize = 11.sp,
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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.glance.text.TextAlign.Center
                    ),
                    maxLines = 2
                )
            }
        }
    }
}
