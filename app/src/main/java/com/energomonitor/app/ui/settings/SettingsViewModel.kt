package com.energomonitor.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.energomonitor.app.data.local.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCredentials()
    }

    private fun loadCredentials() {
        viewModelScope.launch {
            val userId = userPreferences.userId.first() ?: ""
            val token = userPreferences.token.first() ?: ""
            _uiState.value = _uiState.value.copy(
                userId = userId,
                token = token,
                isLoggedIn = userId.isNotBlank() && token.isNotBlank()
            )
        }
    }

    fun onUserIdChanged(newValue: String) {
        _uiState.value = _uiState.value.copy(userId = newValue)
    }

    fun onTokenChanged(newValue: String) {
        _uiState.value = _uiState.value.copy(token = newValue)
    }

    fun saveCredentials() {
        val currentState = _uiState.value
        viewModelScope.launch {
            if (currentState.userId.isNotBlank() && currentState.token.isNotBlank()) {
                userPreferences.saveCredentials(currentState.userId, currentState.token)
                _uiState.value = currentState.copy(isLoggedIn = true, saveSuccess = true)
            }
        }
    }
    
    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}

data class SettingsUiState(
    val userId: String = "",
    val token: String = "",
    val isLoggedIn: Boolean = false,
    val saveSuccess: Boolean = false
)
