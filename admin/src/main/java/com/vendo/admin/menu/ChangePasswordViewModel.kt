package com.vendo.admin.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.ChangePasswordIn
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onOldChange(value: String) {
        _uiState.value = _uiState.value.copy(oldPassword = value, error = null)
    }

    fun onNewChange(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.oldPassword.isBlank() || state.newPassword.isBlank()) {
            _uiState.value = state.copy(error = null)
            _uiState.value = _uiState.value.copy(error = "Fill in both fields")
            return
        }
        _uiState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                api.changePassword(ChangePasswordIn(state.oldPassword, state.newPassword))
                _uiState.value = _uiState.value.copy(isSaving = false, done = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.toUserMessage("We couldn't change your password. Please try again."),
                )
            }
        }
    }
}
