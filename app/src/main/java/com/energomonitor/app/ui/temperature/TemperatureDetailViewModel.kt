package com.energomonitor.app.ui.temperature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.energomonitor.app.data.repository.SensorDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

enum class TimelineRange(val displayName: String, val ms: Long) {
    LAST_24_HOURS("24h", 24L * 60 * 60 * 1000),
    LAST_48_HOURS("48h", 48L * 60 * 60 * 1000),
    LAST_7_DAYS("7d", 7L * 24 * 60 * 60 * 1000),
    LAST_15_DAYS("15d", 15L * 24 * 60 * 60 * 1000),
    LAST_30_DAYS("30d", 30L * 24 * 60 * 60 * 1000)
}

sealed class TemperatureDetailUiState {
    object Loading : TemperatureDetailUiState()
    data class Success(val dataPoints: List<Pair<Long, Double>>) : TemperatureDetailUiState()
    data class Error(val message: String) : TemperatureDetailUiState()
}

@HiltViewModel
class TemperatureDetailViewModel @Inject constructor(
    private val repository: SensorDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TemperatureDetailUiState>(TemperatureDetailUiState.Loading)
    val uiState: StateFlow<TemperatureDetailUiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(TimelineRange.LAST_48_HOURS)
    val selectedRange: StateFlow<TimelineRange> = _selectedRange.asStateFlow()

    private var currentFeedId: String = ""
    private var currentStreamId: String = ""

    fun initialize(feedId: String, streamId: String) {
        if (currentFeedId == feedId && currentStreamId == streamId) return
        currentFeedId = feedId
        currentStreamId = streamId
        fetchData()
    }

    fun selectRange(range: TimelineRange) {
        if (_selectedRange.value != range) {
            _selectedRange.value = range
            fetchData()
        }
    }

    private fun fetchData() {
        if (currentFeedId.isEmpty() || currentStreamId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = TemperatureDetailUiState.Loading
            try {
                val rangeMs = _selectedRange.value.ms
                val data = repository.fetchHistoricalData(currentFeedId, currentStreamId, rangeMs)
                
                // Keep the original order since the API returns oldest first or newest first?
                // The API docs say: "Lists stream’s data points. Returns an array with the data points... When there are more matching data points than limit, the newest ones are returned." 
                // Wait, it says array of arrays, each has timestamp and value. Let's make sure it is sorted chronologically.
                val sortedData = data.sortedBy { it.first }

                _uiState.value = TemperatureDetailUiState.Success(sortedData)
            } catch (e: Exception) {
                _uiState.value = TemperatureDetailUiState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }
}
