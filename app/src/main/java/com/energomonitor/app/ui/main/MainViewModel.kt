package com.energomonitor.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.energomonitor.app.data.local.UserPreferences
import com.energomonitor.app.data.repository.SensorDataRepository
import com.energomonitor.app.domain.model.SensorData
import com.energomonitor.app.domain.model.SensorTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

import kotlinx.coroutines.flow.firstOrNull

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SensorDataRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _selectedTab = MutableStateFlow(SensorTopic.TEMPERATURE)
    val selectedTab: StateFlow<SensorTopic> = _selectedTab.asStateFlow()

    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    init {
        fetchData()
    }

    fun selectTab(topic: SensorTopic) {
        if (_selectedTab.value != topic) {
            _selectedTab.value = topic
            fetchData()
        }
    }

    fun fetchData(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MainUiState.Loading
            try {
                val currentTab = _selectedTab.value
                val currentTime = System.currentTimeMillis()
                
                // Read from persistent cache
                val cachedFlow = userPreferences.getSensorCache(currentTab)
                val cached: Pair<List<SensorData>, Long>? = cachedFlow.firstOrNull()
                
                // Read saved list order
                val orderFlow = userPreferences.getSensorOrder(currentTab)
                val savedOrder: List<String>? = orderFlow.firstOrNull()
                
                // Read font size offset
                val fontSizeOffset = userPreferences.fontSizeOffset.firstOrNull() ?: 0
                
                // Safe extraction
                val cachedData: List<SensorData>? = cached?.first
                val cachedTime: Long? = cached?.second

                var data: List<SensorData>
                val lastUpdate: Long

                if (!forceRefresh && cachedData != null && cachedTime != null && (currentTime - cachedTime) < CACHE_DURATION_MS) {
                    // Use persistent cached data
                    data = cachedData
                    lastUpdate = cachedTime
                } else {
                    // Fetch new data from remote
                    data = repository.fetchSensorDataForTopic(currentTab)
                    lastUpdate = currentTime
                    // Save to persistent cache
                    userPreferences.saveSensorCache(currentTab, data, lastUpdate)
                }
                
                // Sort data by custom saved order
                if (savedOrder != null && savedOrder.isNotEmpty()) {
                    data = data.sortedBy { sensor -> 
                        val index = savedOrder.indexOf(sensor.id)
                        if (index == -1) Int.MAX_VALUE else index 
                    }
                }

                if (data.isEmpty()) {
                    _uiState.value = MainUiState.Empty
                } else {
                    _uiState.value = MainUiState.Success(data, lastUpdate, fontSizeOffset)
                }
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "An unknown error occurred")
            }
        }
    }

    fun reorderSensors(fromIndex: Int, toIndex: Int) {
        val currentState = _uiState.value
        if (currentState is MainUiState.Success) {
            val currentList = currentState.sensors.toMutableList()
            if (fromIndex in currentList.indices && toIndex in currentList.indices) {
                currentList.apply { add(toIndex, removeAt(fromIndex)) }
                _uiState.value = currentState.copy(sensors = currentList)
            }
        }
    }

    fun saveCurrentOrder() {
        val currentState = _uiState.value
        if (currentState is MainUiState.Success) {
            val currentTab = _selectedTab.value
            val currentOrder = currentState.sensors.map { it.id }
            viewModelScope.launch(Dispatchers.IO) {
                userPreferences.saveSensorOrder(currentTab, currentOrder)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearCredentials()
        }
    }
}

sealed class MainUiState {
    object Loading : MainUiState()
    object Empty : MainUiState()
    data class Success(val sensors: List<SensorData>, val lastUpdate: Long, val fontSizeOffset: Int = 0) : MainUiState()
    data class Error(val message: String) : MainUiState()
}
