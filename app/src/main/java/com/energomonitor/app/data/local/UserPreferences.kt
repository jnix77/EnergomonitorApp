package com.energomonitor.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val TOKEN_KEY = stringPreferencesKey("token")
    }

    val userId: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[USER_ID_KEY]
        }

    val token: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[TOKEN_KEY]
        }

    suspend fun saveCredentials(userId: String, token: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(TOKEN_KEY)
            
            // Also clear all cache on logout
            com.energomonitor.app.domain.model.SensorTopic.entries.forEach { topic ->
                preferences.remove(stringPreferencesKey("cache_${topic.name}"))
                preferences.remove(stringPreferencesKey("cache_time_${topic.name}"))
            }
        }
    }

    fun getSensorCache(topic: com.energomonitor.app.domain.model.SensorTopic): Flow<Pair<List<com.energomonitor.app.domain.model.SensorData>, Long>?> = 
        context.dataStore.data.map { preferences ->
            val jsonString = preferences[stringPreferencesKey("cache_${topic.name}")]
            val timestamp = preferences[stringPreferencesKey("cache_time_${topic.name}")]?.toLongOrNull()
            
            if (jsonString != null && timestamp != null) {
                try {
                    val data = kotlinx.serialization.json.Json.decodeFromString<List<com.energomonitor.app.domain.model.SensorData>>(jsonString)
                    Pair(data, timestamp)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

    suspend fun saveSensorCache(topic: com.energomonitor.app.domain.model.SensorTopic, data: List<com.energomonitor.app.domain.model.SensorData>, timestamp: Long) {
        val jsonString = kotlinx.serialization.json.Json.encodeToString(data)
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("cache_${topic.name}")] = jsonString
            preferences[stringPreferencesKey("cache_time_${topic.name}")] = timestamp.toString()
        }
    }
}
