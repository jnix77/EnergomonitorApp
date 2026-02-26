package com.energomonitor.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.energomonitor.app.data.repository.SensorDataRepository
import com.energomonitor.app.domain.model.SensorData
import com.energomonitor.app.domain.model.SensorTopic
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TemperatureWidgetConfigActivity : ComponentActivity() {

    @Inject
    lateinit var sensorDataRepository: SensorDataRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set the default result to CANCELED, so if the user backs out, the widget is not created.
        setResult(RESULT_CANCELED)

        // Find the widget id from the intent
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                var sensors by remember { mutableStateOf<List<SensorData>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    try {
                        sensors = sensorDataRepository.fetchSensorDataForTopic(SensorTopic.TEMPERATURE)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isLoading = false
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Select Temperature Sensor") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                ) { padding ->
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (sensors.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text("No temperature sensors found.")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                        ) {
                            items(sensors) { sensor ->
                                SensorItem(sensor = sensor) {
                                    coroutineScope.launch {
                                        saveWidgetConfigurationAndFinish(sensor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun saveWidgetConfigurationAndFinish(sensor: SensorData) {
        val manager = GlanceAppWidgetManager(this)
        val glanceId = manager.getGlanceIdBy(appWidgetId)
        
        updateAppWidgetState(this, glanceId) { prefs ->
            prefs[TemperatureWidget.feedIdKey] = sensor.feedId
            prefs[TemperatureWidget.streamIdKey] = sensor.id
            prefs[TemperatureWidget.titleKey] = sensor.title
            prefs[TemperatureWidget.valueKey] = sensor.currentValue
            prefs[TemperatureWidget.timestampKey] = sensor.timestamp
        }
        
        val widget = TemperatureWidget()
        widget.update(this, glanceId)
        
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
fun SensorItem(sensor: SensorData, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Thermostat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = sensor.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${sensor.currentValue} ${sensor.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
