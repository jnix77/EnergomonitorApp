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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.energomonitor.app.domain.model.SensorData
import com.energomonitor.app.domain.model.SensorTopic
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Energomonitor Dashboard", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.fetchData(forceRefresh = true) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            val tabs = SensorTopic.entries.toList()
            val selectedTabIndex = tabs.indexOf(selectedTab)

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                tabs.forEachIndexed { index, topic ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.selectTab(topic) },
                        text = { Text(topic.displayName) }
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
                            onDragEnd = { viewModel.saveCurrentOrder() }
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
    onDragEnd: () -> Unit
) {
    val formatter = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    val lastUpdateText = "Last updated: ${formatter.format(java.util.Date(lastUpdate))}"

    val state = rememberReorderableLazyListState(
        onMove = { from, to -> onMove(from.index - 1, to.index - 1) }, // -1 because the first item is the header
        canDragOver = { draggedOver, _ -> draggedOver.index > 0 }, // Prevent dragging over the header
        onDragEnd = { _, _ -> onDragEnd() }
    )

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
            ReorderableItem(reorderableState = state, key = sensor.id) { isDragging ->
                SensorCard(
                    sensor = sensor, 
                    topic = topic, 
                    isDragging = isDragging,
                    modifier = Modifier.detectReorderAfterLongPress(state)
                )
            }
        }
    }
}

@Composable
fun SensorCard(
    sensor: SensorData, 
    topic: SensorTopic, 
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val gradientColors = when (topic) {
        SensorTopic.TEMPERATURE -> listOf(Color(0xFFFF512F), Color(0xFFDD2476))
        SensorTopic.ENERGY -> listOf(Color(0xFFF2C94C), Color(0xFFF2994A))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 16.dp else 8.dp)
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
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sensor.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = sensor.currentValue.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = sensor.unit,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}
