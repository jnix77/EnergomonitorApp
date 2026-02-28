package com.energomonitor.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Provides hardware-backed encrypted storage for sensitive credentials
 * using Android Keystore via EncryptedSharedPreferences.
 *
 * Keys are stored in hardware-backed secure storage and cannot be
 * extracted even from a rooted device.
 */
object SecureStorage {
    private const val PREFS_NAME = "secure_credentials"

    private const val KEY_PASSWORD = "password"
    private const val KEY_TOKEN = "token"
    private const val KEY_TOKEN_EXPIRES = "token_expires_at"

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun savePassword(context: Context, password: String) {
        getEncryptedPrefs(context).edit { putString(KEY_PASSWORD, password) }
    }

    fun getPassword(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_PASSWORD, null)
    }

    fun saveToken(context: Context, token: String, expiresAt: String) {
        getEncryptedPrefs(context).edit {
            putString(KEY_TOKEN, token)
            putString(KEY_TOKEN_EXPIRES, expiresAt)
        }
    }

    fun getToken(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_TOKEN, null)
    }

    fun getTokenExpiresAt(context: Context): String? {
        return getEncryptedPrefs(context).getString(KEY_TOKEN_EXPIRES, null)
    }

    fun clearAll(context: Context) {
        getEncryptedPrefs(context).edit { clear() }
    }
}
