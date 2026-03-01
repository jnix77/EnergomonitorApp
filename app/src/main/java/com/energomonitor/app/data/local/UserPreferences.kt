package com.energomonitor.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val USERNAME_KEY = stringPreferencesKey("username")
        val FONT_SIZE_OFFSET_KEY = intPreferencesKey("font_size_offset")
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val username: Flow<String?> = context.dataStore.data.map { it[USERNAME_KEY] }
    
    // Add font size offset defaulting to 0
    val fontSizeOffset: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[FONT_SIZE_OFFSET_KEY] ?: 0
    }

    // Password and token are now stored in EncryptedSharedPreferences via SecureStorage
    val password: Flow<String?> = context.dataStore.data.map { 
        SecureStorage.getPassword(context)
    }
    val token: Flow<String?> = context.dataStore.data.map { 
        SecureStorage.getToken(context)
    }

    suspend fun saveAuthData(userId: String, token: String, expiresAt: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
        }
        SecureStorage.saveToken(context, token, expiresAt)
    }

    suspend fun saveCredentials(username: String, passwordRaw: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
        }
        if (passwordRaw.isNotBlank()) {
            SecureStorage.savePassword(context, passwordRaw)
        }
    }

    suspend fun saveFontSizeOffset(offset: Int) {
        context.dataStore.edit { preferences ->
            preferences[FONT_SIZE_OFFSET_KEY] = offset
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(USERNAME_KEY)
            
            // Also clear all cache and order on logout
            com.energomonitor.app.domain.model.SensorTopic.entries.forEach { topic ->
                preferences.remove(stringPreferencesKey("cache_${topic.name}"))
                preferences.remove(stringPreferencesKey("cache_time_${topic.name}"))
                preferences.remove(stringPreferencesKey("order_${topic.name}"))
            }
        }
        // Clear encrypted storage
        SecureStorage.clearAll(context)
    }

    fun getSensorCache(topic: com.energomonitor.app.domain.model.SensorTopic): Flow<Pair<List<com.energomonitor.app.domain.model.SensorData>, Long>?> = 
        context.dataStore.data.map { preferences ->
            val jsonString = preferences[stringPreferencesKey("cache_${topic.name}")]
            val timestamp = preferences[stringPreferencesKey("cache_time_${topic.name}")]?.toLongOrNull()
            
            if (jsonString != null && timestamp != null) {
                try {
                    val data = kotlinx.serialization.json.Json.decodeFromString<List<com.energomonitor.app.domain.model.SensorData>>(jsonString)
                    Pair(data, timestamp)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }.flowOn(Dispatchers.IO)

    suspend fun saveSensorCache(topic: com.energomonitor.app.domain.model.SensorTopic, data: List<com.energomonitor.app.domain.model.SensorData>, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val jsonString = kotlinx.serialization.json.Json.encodeToString(data)
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("cache_${topic.name}")] = jsonString
                preferences[stringPreferencesKey("cache_time_${topic.name}")] = timestamp.toString()
            }
        }
    }

    fun getSensorOrder(topic: com.energomonitor.app.domain.model.SensorTopic): Flow<List<String>?> =
        context.dataStore.data.map { preferences ->
            val jsonString = preferences[stringPreferencesKey("order_${topic.name}")]
            if (jsonString != null) {
                try {
                    kotlinx.serialization.json.Json.decodeFromString<List<String>>(jsonString)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }.flowOn(Dispatchers.IO)

    suspend fun saveSensorOrder(topic: com.energomonitor.app.domain.model.SensorTopic, orderIds: List<String>) {
        withContext(Dispatchers.IO) {
            val jsonString = kotlinx.serialization.json.Json.encodeToString(orderIds)
            context.dataStore.edit { preferences ->
                preferences[stringPreferencesKey("order_${topic.name}")] = jsonString
            }
        }
    }
}
