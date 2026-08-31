package com.vendo.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.app.cache.CacheRepository
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.designsystem.VendoThemeMode
import com.vendo.core.network.AuthEventBus
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** One-shot result of a cache refresh, for a Toast/Snackbar - not part of
 * uiState because it's an event (show once, then forget), not a
 * persistent screen state. */
sealed interface CacheRefreshEvent {
    data object Started : CacheRefreshEvent
    data class Succeeded(val customers: Int, val items: Int, val orders: Int) : CacheRefreshEvent
    data class Failed(val message: String) : CacheRefreshEvent
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val cache: CacheRepository,
    authEventBus: AuthEventBus,
) : ViewModel() {

    val authEvents: SharedFlow<com.vendo.core.network.AuthEvent> = authEventBus.events

    private val _cacheRefreshEvents = MutableSharedFlow<CacheRefreshEvent>(extraBufferCapacity = 1)
    val cacheRefreshEvents: SharedFlow<CacheRefreshEvent> = _cacheRefreshEvents

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

    /** Manually re-syncs the offline cache (customers/items/last-30-orders)
     * from the server - see CacheRepository. Deliberately not automatic:
     * a salesman taps Refresh when they want fresh data, not on a timer
     * that could burn their data plan or surprise them mid-order. */
    fun refreshCache() {
        viewModelScope.launch {
            _cacheRefreshEvents.emit(CacheRefreshEvent.Started)
            cache.refresh().fold(
                onSuccess = { result ->
                    _cacheRefreshEvents.emit(
                        CacheRefreshEvent.Succeeded(result.customers, result.items, result.orders),
                    )
                },
                onFailure = { e ->
                    _cacheRefreshEvents.emit(CacheRefreshEvent.Failed(e.toUserMessage()))
                },
            )
        }
    }
}
