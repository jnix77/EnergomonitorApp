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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SensorDataRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        fetchData()
    }

    fun fetchData() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                val data = repository.fetchAndGroupSensorData()
                if (data.isEmpty()) {
                    _uiState.value = MainUiState.Empty
                } else {
                    _uiState.value = MainUiState.Success(data)
                }
            } catch (e: Exception) {
                _uiState.value = MainUiState.Error(e.message ?: "An unknown error occurred")
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
    data class Success(val groupedData: Map<SensorTopic, List<SensorData>>) : MainUiState()
    data class Error(val message: String) : MainUiState()
}
