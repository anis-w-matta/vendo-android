package com.vendo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.designsystem.VendoThemeMode
import com.vendo.core.network.AuthEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val loginId: String) : SessionState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    authEventBus: AuthEventBus,
) : ViewModel() {

    val authEvents: SharedFlow<com.vendo.core.network.AuthEvent> = authEventBus.events

    val themeMode: StateFlow<VendoThemeMode> = settings.isDarkMode
        .map { if (it) VendoThemeMode.DARK else VendoThemeMode.LIGHT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VendoThemeMode.LIGHT)

    // Starts Loading (not assumed LoggedOut) so the nav graph doesn't flash
    // the Login screen for a returning user before DataStore's first real
    // emission arrives.
    val sessionState: StateFlow<SessionState> = settings.loginId
        .map { id -> if (id.isNullOrBlank()) SessionState.LoggedOut else SessionState.LoggedIn(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    fun toggleTheme() {
        viewModelScope.launch {
            settings.setDarkMode(themeMode.value == VendoThemeMode.LIGHT)
        }
    }

    fun logOut() {
        viewModelScope.launch { settings.clearSession() }
    }
}
