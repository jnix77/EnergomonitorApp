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
            val username = userPreferences.username.first() ?: ""
            val hasPassword = !userPreferences.password.first().isNullOrBlank()
            val token = userPreferences.token.first() ?: ""
            val currentOffset = userPreferences.fontSizeOffset.first()
            
            _uiState.value = _uiState.value.copy(
                username = username,
                // Don't prefill password for security/UI reasons, but we check if we have one
                hasStoredPassword = hasPassword,
                isLoggedIn = token.isNotBlank() && hasPassword,
                fontSizeOffset = currentOffset
            )
        }
    }

    fun onUsernameChanged(newValue: String) {
        _uiState.value = _uiState.value.copy(username = newValue)
    }

    fun onPasswordChanged(newValue: String) {
        _uiState.value = _uiState.value.copy(passwordInput = newValue)
    }

    fun onFontSizeChanged(offset: Int) {
        _uiState.value = _uiState.value.copy(fontSizeOffset = offset)
        viewModelScope.launch {
            userPreferences.saveFontSizeOffset(offset)
        }
    }

    fun saveCredentials() {
        val currentState = _uiState.value
        viewModelScope.launch {
            if (currentState.username.isNotBlank() && currentState.passwordInput.isNotBlank()) {
                userPreferences.saveCredentials(currentState.username, currentState.passwordInput)
                // We clear the old token so the interceptor gets a new one on next request
                userPreferences.saveAuthData("", "", "")
                _uiState.value = currentState.copy(isLoggedIn = true, saveSuccess = true, hasStoredPassword = true)
            }
        }
    }
    
    fun resetSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun logout() {
        viewModelScope.launch {
            userPreferences.clearCredentials()
            _uiState.value = SettingsUiState()
        }
    }
}

data class SettingsUiState(
    val username: String = "",
    val passwordInput: String = "",
    val hasStoredPassword: Boolean = false,
    val isLoggedIn: Boolean = false,
    val saveSuccess: Boolean = false,
    val fontSizeOffset: Int = 0
)
