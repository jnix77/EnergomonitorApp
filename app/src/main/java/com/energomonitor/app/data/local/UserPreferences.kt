package com.energomonitor.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        val USER_ID_KEY = stringPreferencesKey("user_id") // Still needed for Energomonitor data API calls
        val USERNAME_KEY = stringPreferencesKey("username")
        val PASSWORD_KEY = stringPreferencesKey("password")
        val TOKEN_KEY = stringPreferencesKey("token")
        val TOKEN_EXPIRES_KEY = stringPreferencesKey("token_expires_at")
    }

    val userId: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val username: Flow<String?> = context.dataStore.data.map { it[USERNAME_KEY] }
    val password: Flow<String?> = context.dataStore.data.map { CryptoUtils.decrypt(it[PASSWORD_KEY]) }
    val token: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val tokenExpiresAt: Flow<String?> = context.dataStore.data.map { it[TOKEN_EXPIRES_KEY] }

    suspend fun saveAuthData(userId: String, token: String, expiresAt: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_ID_KEY] = userId
            preferences[TOKEN_KEY] = token
            preferences[TOKEN_EXPIRES_KEY] = expiresAt
        }
    }

    suspend fun saveCredentials(username: String, passwordRaw: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME_KEY] = username
            if (passwordRaw.isNotBlank()) {
                preferences[PASSWORD_KEY] = CryptoUtils.encrypt(passwordRaw)
            }
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(USERNAME_KEY)
            preferences.remove(PASSWORD_KEY)
            preferences.remove(TOKEN_KEY)
            preferences.remove(TOKEN_EXPIRES_KEY)
            
            // Also clear all cache and order on logout
            com.energomonitor.app.domain.model.SensorTopic.entries.forEach { topic ->
                preferences.remove(stringPreferencesKey("cache_${topic.name}"))
                preferences.remove(stringPreferencesKey("cache_time_${topic.name}"))
                preferences.remove(stringPreferencesKey("order_${topic.name}"))
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
                } catch (e: Exception) {
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
