package com.energomonitor.app.ui.detail

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.energomonitor.app.domain.model.SensorTopic
import com.energomonitor.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    topic: SensorTopic,
    feedId: String,
    streamId: String,
    title: String,
    onNavigateBack: () -> Unit,
    viewModel: SensorDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()

    LaunchedEffect(feedId, streamId) {
        viewModel.initialize(feedId, streamId)
    }

    val chipColor = when (topic) {
        SensorTopic.POWER -> Color(0xFF388E3C) 
        SensorTopic.GAS -> GasGradientStart
        SensorTopic.WATER -> WaterGradientStart
        SensorTopic.HUMIDITY -> HumidityGradientStart
        SensorTopic.CO2 -> Co2GradientStart
        else -> Color.Gray
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Timeline selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Center
            ) {
                SensorTimelineRange.entries.forEach { range ->
                    FilterChip(
                        selected = range == selectedRange,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(range.displayName) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                when (val state = uiState) {
                    is SensorDetailUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is SensorDetailUiState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is SensorDetailUiState.Success -> {
                        if (state.dataPoints.isEmpty()) {
                            Text(
                                "No data available.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            SensorChart(topic = topic, dataPoints = state.dataPoints, selectedRange = selectedRange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorChart(
    topic: SensorTopic,
    dataPoints: List<Pair<Long, Double>>,
    selectedRange: SensorTimelineRange
) {
    val lineBrush = when (topic) {
        SensorTopic.POWER -> androidx.compose.ui.graphics.SolidColor(Color(0xFF2E7D32)) // Dark Green
        SensorTopic.GAS -> androidx.compose.ui.graphics.SolidColor(Color(0xFF7B1FA2)) // Deep Purple
        SensorTopic.WATER -> androidx.compose.ui.graphics.SolidColor(Color(0xFF005C97)) // Blue
        SensorTopic.HUMIDITY -> androidx.compose.ui.graphics.SolidColor(Color(0xFF00695C)) // Teal
        SensorTopic.CO2 -> androidx.compose.ui.graphics.SolidColor(Color(0xFF8E24AA)) // Purple
        else -> androidx.compose.ui.graphics.SolidColor(Color.Gray)
    }

    val areaBrush = when (topic) {
        SensorTopic.POWER -> androidx.compose.ui.graphics.SolidColor(Color(0xFF81C784).copy(alpha = 0.5f))
        SensorTopic.GAS -> androidx.compose.ui.graphics.SolidColor(Color(0xFFCE93D8).copy(alpha = 0.5f))
        SensorTopic.WATER -> androidx.compose.ui.graphics.SolidColor(Color(0xFF64B5F6).copy(alpha = 0.5f))
        SensorTopic.HUMIDITY -> androidx.compose.ui.graphics.SolidColor(Color(0xFF4DB6AC).copy(alpha = 0.5f))
        SensorTopic.CO2 -> androidx.compose.ui.graphics.SolidColor(Color(0xFFE1BEE7).copy(alpha = 0.5f))
        else -> androidx.compose.ui.graphics.SolidColor(Color.LightGray.copy(alpha = 0.5f))
    }

    // We expect timestamps in seconds, so multiply by 1000 for standard format
    val minTimestamp = dataPoints.minOfOrNull { it.first } ?: 0L
    val maxTimestamp = dataPoints.maxOfOrNull { it.first } ?: 1L
    
    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffsetX by remember { mutableFloatStateOf(0f) }
    var canvasWidthPx by remember { mutableFloatStateOf(100f) }
    
    // Reset zoom when timeframe changes
    LaunchedEffect(dataPoints) {
        currentScale = 1f
        currentOffsetX = 0f
    }

    val paddedMin: Double
    val paddedMax: Double
    val yStep: Int
    
    // Calculate the total time range
    val timeRange = maxTimestamp - minTimestamp
    val actualTimeRange = if (timeRange == 0L) 1L else timeRange
    
    // Determine the exact timeframe that is currently visible based on pinch/pan
    val visibleStartTime = minTimestamp + ((-currentOffsetX / (canvasWidthPx * currentScale)) * actualTimeRange).toLong()
    val visibleEndTime = minTimestamp + (((canvasWidthPx - currentOffsetX) / (canvasWidthPx * currentScale)) * actualTimeRange).toLong()
    
    // Map visible data subset
    val visibleData = dataPoints.filter { it.first in visibleStartTime..visibleEndTime }
    val minValue = visibleData.minOfOrNull { it.second } ?: dataPoints.minOfOrNull { it.second } ?: 0.0
    val maxValue = visibleData.maxOfOrNull { it.second } ?: dataPoints.maxOfOrNull { it.second } ?: 100.0

    // Prevent rounding completely to 1 unless step is tiny, as value could be floating
    val minY = kotlin.math.floor(minValue).toInt()
    val maxY = kotlin.math.ceil(maxValue).toInt()
    val yRange = kotlin.math.max(1, maxY - minY)

    val targetTickCount = 5.0
    val rawStep = yRange.toDouble() / targetTickCount
    val magnitude = java.lang.Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(rawStep)))
    val normalizedStep = rawStep / magnitude
    
    val niceStepMultiplier = when {
        normalizedStep < 1.5 -> 1.0
        normalizedStep < 3.0 -> 2.0
        normalizedStep < 7.0 -> 5.0
        else -> 10.0
    }
    
    yStep = kotlin.math.max(1, (niceStepMultiplier * magnitude).toInt())

    paddedMin = ((minY / yStep) - 1).coerceAtLeast(0) * yStep.toDouble() // coerce at 0
    paddedMax = ((maxY / yStep) + 1) * yStep.toDouble()

    val timeFormat = remember(selectedRange) {
        when (selectedRange) {
            SensorTimelineRange.LAST_24_HOURS, SensorTimelineRange.LAST_48_HOURS -> SimpleDateFormat("HH:mm", Locale.getDefault())
            else -> SimpleDateFormat("MMM dd", Locale.getDefault())
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 32.dp, bottom = 32.dp, start = 48.dp, end = 16.dp)
                    .onSizeChanged { size -> 
                        if (size.width > 0) canvasWidthPx = size.width.toFloat()
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            // Calculate potential new scale
                            val oldScale = currentScale
                            currentScale = (currentScale * zoom).coerceIn(1f, 100f)
                            
                            // Maintain the focal point relative view when zooming
                            val focalRatio = centroid.x / size.width
                            val widthOffsetPixels = (currentScale - oldScale) * size.width
                            val offsetAdjust = widthOffsetPixels * focalRatio
                            
                            // Apply pan and zoom compensation
                            val proposedOffset = currentOffsetX + pan.x - offsetAdjust
                            
                            // Restrict pan bounds, meaning we can't pan left past 0 or right past the scaled over-width
                            val maxPanPx = 0f
                            val minPanPx = -size.width * (currentScale - 1f)
                            currentOffsetX = proposedOffset.coerceIn(minPanPx, maxPanPx)
                        }
                    }
            ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Time and value mapping
            fun mapX(timestamp: Long): Float {
                // Determine absolute position of timestamp spanning 0..1 then multiply mapped to canvasWidth
                val basePosition = ((timestamp - minTimestamp).toFloat() / actualTimeRange) * canvasWidth
                // Apply our current zoom/pan state
                return (basePosition * currentScale) + currentOffsetX
            }

            fun mapY(value: Double): Float {
                val normalized = (value - paddedMin) / (paddedMax - paddedMin)
                return canvasHeight - (normalized.toFloat() * canvasHeight)
            }

            // Draw Y-axis grid and labels
            val textPaint = Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 40f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.RIGHT
            }

            var currentY = paddedMin.toInt()
            while (currentY <= paddedMax.toInt()) {
                val yFraction = (currentY - paddedMin) / (paddedMax - paddedMin)
                val yPos = canvasHeight - (yFraction.toFloat() * canvasHeight)

                // Draw horizontal line
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(0f, yPos),
                    end = Offset(canvasWidth, yPos),
                    strokeWidth = 2f
                )

                // Draw text
                drawContext.canvas.nativeCanvas.drawText(
                    currentY.toString(),
                    -16f,
                    yPos + 12f,
                    textPaint
                )
                
                currentY += yStep
            }

            // Draw X-axis grid lines depending on selected range and scale
            val baseStepSeconds = when (selectedRange) {
                SensorTimelineRange.LAST_24_HOURS -> 2 * 3600L
                SensorTimelineRange.LAST_48_HOURS -> 4 * 3600L
                SensorTimelineRange.LAST_7_DAYS -> 24 * 3600L
                SensorTimelineRange.LAST_15_DAYS -> 3 * 24 * 3600L
                SensorTimelineRange.LAST_30_DAYS -> 5 * 24 * 3600L
            }
            
            // Dynamically scale down the time step when deeply zoomed in
            val stepDivisor = when {
                currentScale >= 50f -> 24L
                currentScale >= 20f -> 12L
                currentScale >= 10f -> 6L
                currentScale >= 5f -> 3L
                currentScale >= 2f -> 2L
                else -> 1L
            }
            
            val stepSeconds = Math.max(3600L, baseStepSeconds / stepDivisor) // Never drop below 1 hour intervals
            
            val formatType = if (stepSeconds <= 12 * 3600L && selectedRange != SensorTimelineRange.LAST_24_HOURS && selectedRange != SensorTimelineRange.LAST_48_HOURS) {
                // If we've zoomed into less than 12h chunks on a multi-day view, show Day+Hour
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            } else {
                timeFormat
            }
            
            val calendar = Calendar.getInstance().apply { timeInMillis = minTimestamp * 1000L }
            
            // We lock the calendar alignment to 1h bounds for sub-day steps, and midnight for multi-day steps
            if (stepSeconds < 24 * 3600L) {
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.add(Calendar.HOUR_OF_DAY, 1) // jump to next whole hour
                
                // Align to the specific step block intervals (e.g. 00:00, 04:00, 08:00)
                val hours = calendar.get(Calendar.HOUR_OF_DAY)
                val stepHours = Math.max(1, (stepSeconds / 3600L).toInt())
                val remainder = hours % stepHours
                if (remainder != 0) {
                    calendar.add(Calendar.HOUR_OF_DAY, stepHours - remainder)
                }
            } else {
                // Align exactly to midnight for day+ intervals
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.add(Calendar.DAY_OF_YEAR, 1) 
            }
            
            var currentGridTime = calendar.timeInMillis / 1000L
            
            val labelTextPaint = Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 36f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            var gridIndex = 0
            var lastDrawnLabelRightX = -1000f
            while (currentGridTime <= maxTimestamp) {
                val xPos = mapX(currentGridTime)
                
                // Hide labels and gridlines that span entirely off-screen
                if (xPos in -100f..(canvasWidth + 100f)) {
                    // Draw vertical line
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, canvasHeight),
                        strokeWidth = 2f
                    )
    
                    if (dataPoints.isNotEmpty()) {
                        val labelText = formatType.format(Date(currentGridTime * 1000L))
                        val textWidth = labelTextPaint.measureText(labelText)
                        
                        val leftBound = when {
                            xPos < 50f -> xPos
                            xPos > canvasWidth - 50f -> xPos - textWidth
                            else -> xPos - textWidth / 2f
                        }
                        
                        if (leftBound > lastDrawnLabelRightX + 20f || lastDrawnLabelRightX == -1000f) {
                            val align = when {
                                xPos < 50f -> Paint.Align.LEFT
                                xPos > canvasWidth - 50f -> Paint.Align.RIGHT
                                else -> Paint.Align.CENTER
                            }
                            labelTextPaint.textAlign = align
                            drawContext.canvas.nativeCanvas.drawText(labelText, xPos, canvasHeight + 56f, labelTextPaint)
                            
                            lastDrawnLabelRightX = leftBound + textWidth
                        }
                    }
                }
                
                currentGridTime += stepSeconds
                gridIndex++
            }

            // Draw Paths
            val linePath = Path()
            val fillPath = Path()
            
            dataPoints.forEachIndexed { index, pair ->
                val x = mapX(pair.first)
                val y = mapY(pair.second)
                
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, canvasHeight)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                
                if (index == dataPoints.lastIndex) {
                    fillPath.lineTo(x, canvasHeight)
                    fillPath.close()
                }
            }
            
            if (dataPoints.isNotEmpty()) {
                // Ensure lines do not draw into the Y-axis space when panning
                clipRect(left = 0f, top = 0f, right = canvasWidth, bottom = canvasHeight) {
                    // Draw filled area
                    drawPath(
                        path = fillPath,
                        brush = areaBrush
                    )
                    
                    // Draw top stroke line
                    drawPath(
                        path = linePath,
                        brush = lineBrush,
                        style = Stroke(
                            width = 4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
            
            // Accessible Zoom Controls Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 12.dp)
                    .background(Color(0xFF333333).copy(alpha=0.8f), RoundedCornerShape(12.dp)),
            ) {
                IconButton(
                    onClick = { 
                        currentScale = (currentScale * 1.5f).coerceIn(1f, 100f)
                        // Adjust offset toward center
                        val offsetAdjust = ((currentScale - (currentScale / 1.5f)) * canvasWidthPx) * 0.5f
                        val proposed = currentOffsetX - offsetAdjust
                        val minPanPx = -canvasWidthPx * (currentScale - 1f)
                        currentOffsetX = proposed.coerceIn(minPanPx, 0f)
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
                }
            IconButton(
                onClick = { 
                    currentScale = (currentScale / 1.5f).coerceIn(1f, 100f)
                    // Adjust bounds since we shrank
                    val offsetAdjust = (((currentScale * 1.5f) - currentScale) * canvasWidthPx) * 0.5f
                    val proposed = currentOffsetX + offsetAdjust
                    val minPanPx = -canvasWidthPx * (currentScale - 1f)
                    currentOffsetX = proposed.coerceIn(minPanPx, 0f)
                }
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
        }
        }
    }
}
