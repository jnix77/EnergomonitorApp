package com.energomonitor.app.ui.temperature

import android.graphics.Paint
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.energomonitor.app.ui.theme.TemperatureGradientEnd
import com.energomonitor.app.ui.theme.TemperatureGradientStart
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemperatureDetailScreen(
    feedId: String,
    streamId: String,
    title: String,
    onNavigateBack: () -> Unit,
    viewModel: TemperatureDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRange by viewModel.selectedRange.collectAsState()

    LaunchedEffect(feedId, streamId) {
        viewModel.initialize(feedId, streamId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TimelineRange.entries.forEach { range ->
                    FilterChip(
                        selected = range == selectedRange,
                        onClick = { viewModel.selectRange(range) },
                        label = { Text(range.displayName) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TemperatureGradientStart,
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
                    is TemperatureDetailUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is TemperatureDetailUiState.Error -> {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    is TemperatureDetailUiState.Success -> {
                        if (state.dataPoints.isEmpty()) {
                            Text(
                                "No data available.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            TemperatureChart(dataPoints = state.dataPoints, selectedRange = selectedRange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemperatureChart(
    dataPoints: List<Pair<Long, Double>>,
    selectedRange: TimelineRange
) {
    val gradientColors = listOf(TemperatureGradientStart, TemperatureGradientEnd)
    
    // We expect timestamps in seconds, so multiply by 1000 for standard format
    val minTimestamp = dataPoints.minOfOrNull { it.first } ?: 0L
    val maxTimestamp = dataPoints.maxOfOrNull { it.first } ?: 1L
    
    val minValue = dataPoints.minOfOrNull { it.second } ?: 0.0
    val maxValue = dataPoints.maxOfOrNull { it.second } ?: 100.0

    val minY = kotlin.math.floor(minValue).toInt()
    val maxY = kotlin.math.ceil(maxValue).toInt()
    val yRange = kotlin.math.max(1, maxY - minY)

    val yStep = when {
        yRange <= 5 -> 1
        yRange <= 10 -> 2
        yRange <= 20 -> 4
        yRange <= 40 -> 5
        else -> 10
    }

    val paddedMin = ((minY / yStep) - 1) * yStep.toDouble()
    val paddedMax = ((maxY / yStep) + 1) * yStep.toDouble()

    val timeFormat = remember(selectedRange) {
        when (selectedRange) {
            TimelineRange.LAST_24_HOURS, TimelineRange.LAST_48_HOURS -> SimpleDateFormat("HH:mm", Locale.getDefault())
            else -> SimpleDateFormat("MMM dd", Locale.getDefault())
        }
    }

    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp, bottom = 32.dp, start = 48.dp, end = 16.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Time and value mapping
            val timeRange = maxTimestamp - minTimestamp
            val actualTimeRange = if (timeRange == 0L) 1L else timeRange

            fun mapX(timestamp: Long): Float {
                return ((timestamp - minTimestamp).toFloat() / actualTimeRange) * canvasWidth
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

            // Draw X-axis grid lines depending on selected range
            val stepSeconds = when (selectedRange) {
                TimelineRange.LAST_24_HOURS -> 2 * 3600L
                TimelineRange.LAST_48_HOURS -> 4 * 3600L
                TimelineRange.LAST_7_DAYS -> 24 * 3600L
                TimelineRange.LAST_15_DAYS -> 3 * 24 * 3600L
                TimelineRange.LAST_30_DAYS -> 5 * 24 * 3600L
            }
            
            val calendar = Calendar.getInstance().apply { timeInMillis = minTimestamp * 1000L }
            if (selectedRange == TimelineRange.LAST_24_HOURS || selectedRange == TimelineRange.LAST_48_HOURS) {
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                val hours = calendar.get(Calendar.HOUR_OF_DAY)
                val stepHours = (stepSeconds / 3600L).toInt()
                val remainder = hours % stepHours
                if (remainder != 0) {
                    calendar.add(Calendar.HOUR_OF_DAY, stepHours - remainder)
                }
            } else {
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
            while (currentGridTime <= maxTimestamp) {
                val xPos = mapX(currentGridTime)
                
                // Draw vertical line
                drawLine(
                    color = Color.Gray.copy(alpha = 0.15f),
                    start = Offset(xPos, 0f),
                    end = Offset(xPos, canvasHeight),
                    strokeWidth = 2f
                )

                // Only draw text for every other grid line to prevent overlapping
                if (dataPoints.isNotEmpty() && gridIndex % 2 == 0) {
                    val labelText = timeFormat.format(Date(currentGridTime * 1000L))
                    val align = when {
                        xPos < 50f -> Paint.Align.LEFT
                        xPos > canvasWidth - 50f -> Paint.Align.RIGHT
                        else -> Paint.Align.CENTER
                    }
                    labelTextPaint.textAlign = align
                    drawContext.canvas.nativeCanvas.drawText(labelText, xPos, canvasHeight + 56f, labelTextPaint)
                }
                
                currentGridTime += stepSeconds
                gridIndex++
            }

            // Define color stops for the sharp changes
            val blueColor = Color(0xFF64B5F6)     // Blue for < 0°C
            val greenColor = Color(0xFF81C784)    // Green for 0°C - 10°C
            val yellowColor = Color(0xFFFFD54F)   // Yellow for 10°C - 20°C
            val orangeColor = Color(0xFFFFB74D)   // Orange for 20°C - 30°C
            val redColor = Color(0xFFE57373)      // Red for >= 30°C
            
            val colorStops = mutableListOf<Pair<Float, Color>>()
            val alphaStops = mutableListOf<Pair<Float, Color>>()
            
            var lastColor: Color? = null
            dataPoints.forEachIndexed { index, pair ->
                val xFraction = if (canvasWidth > 0f) (mapX(pair.first) / canvasWidth).coerceIn(0f, 1f) else 0f
                val baseColor = when {
                    pair.second >= 30.0 -> redColor
                    pair.second >= 20.0 -> orangeColor
                    pair.second >= 10.0 -> yellowColor
                    pair.second >= 0.0 -> greenColor
                    else -> blueColor
                }
                
                // If color changes, insert a sharp boundary instead of blending
                if (baseColor != lastColor) {
                    if (lastColor != null && index > 0) {
                        // Previous color goes all the way right up to this exact fraction
                        colorStops.add(xFraction to lastColor!!)
                        alphaStops.add(xFraction to lastColor!!.copy(alpha = 0.6f))
                    }
                    
                    // New color starts exactly at this fraction
                    colorStops.add(xFraction to baseColor)
                    alphaStops.add(xFraction to baseColor.copy(alpha = 0.6f))
                    
                    lastColor = baseColor
                } else if (index == 0 || index == dataPoints.lastIndex) {
                    colorStops.add(xFraction to baseColor)
                    alphaStops.add(xFraction to baseColor.copy(alpha = 0.6f))
                }
            }

            val temperatureBrush = if (colorStops.size >= 2) {
                Brush.horizontalGradient(colorStops = colorStops.toTypedArray(), startX = 0f, endX = canvasWidth)
            } else {
                androidx.compose.ui.graphics.SolidColor(if (colorStops.isNotEmpty()) colorStops.first().second else orangeColor)
            }
            
            val areaBrush = if (alphaStops.size >= 2) {
                Brush.horizontalGradient(colorStops = alphaStops.toTypedArray(), startX = 0f, endX = canvasWidth)
            } else {
                androidx.compose.ui.graphics.SolidColor(if (alphaStops.isNotEmpty()) alphaStops.first().second else orangeColor.copy(alpha=0.6f))
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
                // Draw filled area
                drawPath(
                    path = fillPath,
                    brush = areaBrush
                )
                
                // Draw top stroke line using the temperature brush instead of generic gradient
                drawPath(
                    path = linePath,
                    brush = temperatureBrush,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}