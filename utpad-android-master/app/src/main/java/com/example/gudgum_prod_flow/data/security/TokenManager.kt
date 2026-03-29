package com.example.gudgum_prod_flow.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages JWT access and refresh tokens using encrypted DataStore.
 */
@Singleton
class TokenManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val accessToken: Flow<String?> = dataStore.data.map { it[ACCESS_TOKEN_KEY] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[REFRESH_TOKEN_KEY] }

    val isLoggedIn: Flow<Boolean> = dataStore.data.map { prefs ->
        val token = prefs[ACCESS_TOKEN_KEY]
        val expiry = prefs[TOKEN_EXPIRY_KEY] ?: 0L
        !token.isNullOrBlank() && System.currentTimeMillis() < expiry
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
            prefs[TOKEN_EXPIRY_KEY] = System.currentTimeMillis() + (expiresInSeconds * 1000)
        }
    }

    suspend fun getAccessToken(): String? {
        return dataStore.data.map { it[ACCESS_TOKEN_KEY] }.firstOrNull()
    }

    suspend fun getRefreshToken(): String? {
        return dataStore.data.map { it[REFRESH_TOKEN_KEY] }.firstOrNull()
    }

    suspend fun clearTokens() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(TOKEN_EXPIRY_KEY)
        }
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val TOKEN_EXPIRY_KEY = longPreferencesKey("token_expiry")
    }
}
