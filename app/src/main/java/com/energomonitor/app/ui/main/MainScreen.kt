package com.energomonitor.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Co2
import androidx.compose.material.icons.rounded.ElectricMeter
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.energomonitor.app.domain.model.SensorData
import com.energomonitor.app.domain.model.SensorTopic
import com.energomonitor.app.ui.theme.*
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

import androidx.compose.ui.graphics.vector.ImageVector
import android.text.format.DateUtils

val SensorTopic.icon: ImageVector
    get() = when (this) {
        SensorTopic.TEMPERATURE -> Icons.Rounded.Thermostat
        SensorTopic.ENERGY -> Icons.Rounded.ElectricMeter
        SensorTopic.DEVICES -> Icons.Rounded.ElectricalServices
        SensorTopic.GAS -> Icons.Rounded.LocalGasStation
        SensorTopic.WATER -> Icons.Rounded.WaterDrop
        SensorTopic.HUMIDITY -> Icons.Rounded.InvertColors
        SensorTopic.CO2 -> Icons.Rounded.Co2
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToTemperatureDetail: (String, String, String) -> Unit,
    onNavigateToEnergyDetail: (String, String, String) -> Unit,
    onNavigateToGasDetail: (String, String, String) -> Unit,
    onNavigateToWaterDetail: (String, String, String) -> Unit,
    onNavigateToHumidityDetail: (String, String, String) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Energomonitor", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { viewModel.fetchData(forceRefresh = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val tabs = SensorTopic.entries.filter { it != SensorTopic.ENERGY }
            val selectedTabIndex = Math.max(0, tabs.indexOf(selectedTab))

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {} // Remove default underline divider
            ) {
                tabs.forEachIndexed { index, topic ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.selectTab(topic) },
                        icon = { 
                            Icon(
                                imageVector = topic.icon, 
                                contentDescription = topic.displayName
                            ) 
                        },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (val state = uiState) {
                    is MainUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is MainUiState.Empty -> {
                        Text(
                            "No data available.",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    is MainUiState.Error -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchData(forceRefresh = true) }) {
                                Text("Retry")
                            }
                        }
                    }
                    is MainUiState.Success -> {
                        DashboardContent(
                            sensors = state.sensors, 
                            topic = selectedTab, 
                            lastUpdate = state.lastUpdate,
                            onMove = { from, to -> viewModel.reorderSensors(from, to) },
                            onDragEnd = { viewModel.saveCurrentOrder() },
                            onNavigateToTemperatureDetail = onNavigateToTemperatureDetail,
                            onNavigateToEnergyDetail = onNavigateToEnergyDetail,
                            onNavigateToGasDetail = onNavigateToGasDetail,
                            onNavigateToWaterDetail = onNavigateToWaterDetail,
                            onNavigateToHumidityDetail = onNavigateToHumidityDetail
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    sensors: List<SensorData>, 
    topic: SensorTopic, 
    lastUpdate: Long,
    onMove: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    onNavigateToTemperatureDetail: (String, String, String) -> Unit,
    onNavigateToEnergyDetail: (String, String, String) -> Unit,
    onNavigateToGasDetail: (String, String, String) -> Unit,
    onNavigateToWaterDetail: (String, String, String) -> Unit,
    onNavigateToHumidityDetail: (String, String, String) -> Unit
) {
    val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    val lastUpdateText = "Last updated: ${formatter.format(java.util.Date(lastUpdate))}"

    val state = rememberReorderableLazyListState(
        onMove = { from, to -> onMove(from.index - 1, to.index - 1) }, // -1 because the first item is the header
        canDragOver = { draggedOver, _ -> draggedOver.index > 0 }, // Prevent dragging over the header
        onDragEnd = { _, _ -> onDragEnd() }
    )

    val currentTime = remember { System.currentTimeMillis() }

    LazyColumn(
        state = state.listState,
        modifier = Modifier.reorderable(state),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = lastUpdateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(sensors, key = { it.id }) { sensor ->
            val isOutdated = remember(sensor.timestamp) { 
                (currentTime - (sensor.timestamp * 1000)) > (2 * 60 * 60 * 1000L) 
            }
            val timeText = remember(sensor.timestamp) {
                DateUtils.getRelativeTimeSpanString(
                    sensor.timestamp * 1000,
                    currentTime,
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            }
            ReorderableItem(reorderableState = state, key = sensor.id) { isDragging ->
                SensorCard(
                    sensor = sensor, 
                    topic = topic, 
                    isDragging = isDragging,
                    timeText = timeText,
                    isOutdated = isOutdated,
                    onClick = {
                        if (topic == SensorTopic.TEMPERATURE) {
                            onNavigateToTemperatureDetail(sensor.feedId, sensor.id, sensor.title)
                        } else if (topic == SensorTopic.ENERGY || topic == SensorTopic.DEVICES) {
                            onNavigateToEnergyDetail(sensor.feedId, sensor.id, sensor.title)
                        } else if (topic == SensorTopic.GAS) {
                            onNavigateToGasDetail(sensor.feedId, sensor.id, sensor.title)
                        } else if (topic == SensorTopic.WATER) {
                            onNavigateToWaterDetail(sensor.feedId, sensor.id, sensor.title)
                        } else if (topic == SensorTopic.HUMIDITY) {
                            onNavigateToHumidityDetail(sensor.feedId, sensor.id, sensor.title)
                        }
                    },
                    modifier = Modifier.detectReorderAfterLongPress(state)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorCard(
    sensor: SensorData, 
    topic: SensorTopic, 
    isDragging: Boolean = false,
    timeText: String,
    isOutdated: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val gradientColors = if (isOutdated) {
        listOf(Color.DarkGray, Color.DarkGray)
    } else {
        when (topic) {
            SensorTopic.TEMPERATURE -> listOf(TemperatureGradientStart, TemperatureGradientEnd)
            SensorTopic.ENERGY -> listOf(EnergyGradientStart, EnergyGradientEnd)
            SensorTopic.DEVICES -> listOf(DevicesGradientStart, DevicesGradientEnd)
            SensorTopic.GAS -> listOf(GasGradientStart, GasGradientEnd)
            SensorTopic.WATER -> listOf(WaterGradientStart, WaterGradientEnd)
            SensorTopic.HUMIDITY -> listOf(HumidityGradientStart, HumidityGradientEnd)
            SensorTopic.CO2 -> listOf(Co2GradientStart, Co2GradientEnd)
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(24.dp), // Premium smooth corner
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 16.dp else 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle icon
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Reorder",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 12.dp)
                )
                
                // Sensor Context Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = topic.icon,
                        contentDescription = "Sensor Type",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sensor.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = timeText,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = sensor.currentValue.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sensor.unit,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}
