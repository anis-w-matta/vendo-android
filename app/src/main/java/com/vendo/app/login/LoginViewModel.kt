package com.vendo.app.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.network.ApiService
import com.vendo.core.network.BuildConfig
import com.vendo.core.network.dto.LoginIn
import com.vendo.core.network.isConnectivityFailure
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val id: String = "",
    val password: String = "",
    val serverUrl: String = "",
    /** True once we know whether a server address was already configured on
     * this device (vs. still on the compiled-in placeholder) - drives
     * whether the Login screen expands the server field by default, so a
     * fresh install still surfaces it for first-time setup without showing
     * it to every rep on every ordinary login thereafter. */
    val serverUrlKnown: Boolean = false,
    val serverUrlConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: ApiService,
    private val settings: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = settings.currentServerUrl()?.takeIf { it.isNotBlank() }
            _uiState.value = _uiState.value.copy(
                serverUrl = stored ?: BuildConfig.BASE_URL,
                serverUrlKnown = true,
                serverUrlConfigured = stored != null,
            )
        }
    }

    fun onIdChange(value: String) {
        _uiState.value = _uiState.value.copy(id = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onServerUrlChange(value: String) {
        _uiState.value = _uiState.value.copy(serverUrl = value, error = null)
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.id.isBlank() || state.password.isBlank() || state.serverUrl.isBlank()) {
            _uiState.value = state.copy(error = "Enter server address, ID and password")
            return
        }
        _uiState.value = state.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                settings.setServerUrl(state.serverUrl.trim())
                val result = api.login(LoginIn(login_id = state.id, password = state.password))
                settings.saveSession(result.token, result.login_id)
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (e.isConnectivityFailure()) {
                        "We couldn't reach that server. Check the address and your connection."
                    } else {
                        e.toUserMessage("That ID or password wasn't recognized.")
                    },
                )
            }
        }
    }
}
