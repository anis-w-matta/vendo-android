package com.vendo.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.designsystem.VendoThemeMode
import com.vendo.core.network.AuthEvent
import com.vendo.core.network.AuthEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AdminSessionState {
    data object Loading : AdminSessionState
    data object LoggedOut : AdminSessionState
    data class LoggedIn(val loginId: String) : AdminSessionState
}

/** Theme + session/auth-event plumbing for the admin app - the
 * :app equivalent (AppViewModel) also owns offline-cache refresh, which
 * this app has no need for: every admin screen here is a direct,
 * pull-to-refresh network read, not a voice-intake flow that needs
 * offline customer/item search. */
@HiltViewModel
class AdminAppViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    authEventBus: AuthEventBus,
) : ViewModel() {

    val authEvents: SharedFlow<AuthEvent> = authEventBus.events

    val themeMode: StateFlow<VendoThemeMode> = settings.isDarkMode
        .map { if (it) VendoThemeMode.DARK else VendoThemeMode.LIGHT }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VendoThemeMode.LIGHT)

    // Starts Loading (not assumed LoggedOut) so the nav graph doesn't flash
    // the Login screen before DataStore's first real emission arrives -
    // same reasoning as :app's AppViewModel. A stored session here is
    // always an admin one: AdminLoginViewModel only calls saveSession()
    // after confirming role == "admin", so no separate role re-check is
    // needed at this layer.
    val sessionState: StateFlow<AdminSessionState> = settings.loginId
        .map { id -> if (id.isNullOrBlank()) AdminSessionState.LoggedOut else AdminSessionState.LoggedIn(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdminSessionState.Loading)

    fun toggleTheme() {
        viewModelScope.launch {
            settings.setDarkMode(themeMode.value == VendoThemeMode.LIGHT)
        }
    }

    fun logOut() {
        viewModelScope.launch { settings.clearSession() }
    }
}
