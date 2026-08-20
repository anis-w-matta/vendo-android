package com.vendo.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vendo_settings")

private object Keys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val AUTH_TOKEN = stringPreferencesKey("auth_token")
    val LOGIN_ID = stringPreferencesKey("login_id")
    val SERVER_URL = stringPreferencesKey("server_url")
}

/** Backs the theme toggle (explicit LIGHT/DARK, persisted across launches)
 * and the logged-in salesman's session - token/login_id cached from
 * POST /auth/login so the app can stay signed in between launches. */
@Singleton
class SettingsDataStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.dataStore

    val isDarkMode: Flow<Boolean> = store.data.map { it[Keys.DARK_MODE] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        store.edit { it[Keys.DARK_MODE] = enabled }
    }

    val authToken: Flow<String?> = store.data.map { it[Keys.AUTH_TOKEN] }

    suspend fun currentToken(): String? = authToken.first()

    val loginId: Flow<String?> = store.data.map { it[Keys.LOGIN_ID] }

    suspend fun saveSession(token: String, loginId: String) {
        store.edit {
            it[Keys.AUTH_TOKEN] = token
            it[Keys.LOGIN_ID] = loginId
        }
    }

    suspend fun clearSession() {
        store.edit {
            it.remove(Keys.AUTH_TOKEN)
            it.remove(Keys.LOGIN_ID)
        }
    }

    /** Salesman-entered backend address (Login screen), applied per-request
     * by ServerUrlInterceptor - lets one build point at any network without
     * a recompile. Null/blank means "use the compiled-in default". */
    val serverUrl: Flow<String?> = store.data.map { it[Keys.SERVER_URL] }

    suspend fun currentServerUrl(): String? = serverUrl.first()

    suspend fun setServerUrl(url: String) {
        store.edit { it[Keys.SERVER_URL] = url }
    }
}
