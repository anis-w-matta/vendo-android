package com.vendo.admin.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.AccountUpdateIn
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val name: String = "",
    val email: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() {
        _uiState.value = AccountUiState(isLoading = true)
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val me = api.me()
                _uiState.value = AccountUiState(
                    isLoading = false, name = me.name, email = me.email ?: "",
                )
            } catch (e: Exception) {
                _uiState.value = AccountUiState(
                    isLoading = false,
                    loadFailed = true,
                    error = e.toUserMessage("We couldn't load your account."),
                )
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, saved = false)
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, saved = false)
    }

    fun save() {
        val state = _uiState.value
        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                api.updateMe(AccountUpdateIn(name = state.name, email = state.email))
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toUserMessage("We couldn't save your changes."),
                )
            }
        }
    }
}
