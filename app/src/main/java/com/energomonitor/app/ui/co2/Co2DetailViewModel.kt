package com.energomonitor.app.ui.co2

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

enum class Co2TimelineRange(val displayName: String, val ms: Long) {
    LAST_24_HOURS("24h", 24L * 60 * 60 * 1000),
    LAST_48_HOURS("48h", 48L * 60 * 60 * 1000),
    LAST_7_DAYS("7d", 7L * 24 * 60 * 60 * 1000),
    LAST_15_DAYS("15d", 15L * 24 * 60 * 60 * 1000),
    LAST_30_DAYS("30d", 30L * 24 * 60 * 60 * 1000)
}

sealed class Co2DetailUiState {
    object Loading : Co2DetailUiState()
    data class Success(val dataPoints: List<Pair<Long, Double>>) : Co2DetailUiState()
    data class Error(val message: String) : Co2DetailUiState()
}

@HiltViewModel
class Co2DetailViewModel @Inject constructor(
    private val repository: SensorDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<Co2DetailUiState>(Co2DetailUiState.Loading)
    val uiState: StateFlow<Co2DetailUiState> = _uiState.asStateFlow()

    private val _selectedRange = MutableStateFlow(Co2TimelineRange.LAST_48_HOURS)
    val selectedRange: StateFlow<Co2TimelineRange> = _selectedRange.asStateFlow()

    private var currentFeedId: String = ""
    private var currentStreamId: String = ""

    fun initialize(feedId: String, streamId: String) {
        if (currentFeedId == feedId && currentStreamId == streamId) return
        currentFeedId = feedId
        currentStreamId = streamId
        fetchData()
    }

    fun selectRange(range: Co2TimelineRange) {
        if (_selectedRange.value != range) {
            _selectedRange.value = range
            fetchData()
        }
    }

    private fun fetchData() {
        if (currentFeedId.isEmpty() || currentStreamId.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = Co2DetailUiState.Loading
            try {
                val rangeMs = _selectedRange.value.ms
                val data = repository.fetchHistoricalData(currentFeedId, currentStreamId, rangeMs)
                
                val sortedData = data.sortedBy { it.first }

                _uiState.value = Co2DetailUiState.Success(sortedData)
            } catch (e: Exception) {
                _uiState.value = Co2DetailUiState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }
}
