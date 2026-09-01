package com.vendo.admin.salesmen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.RegisterIn
import com.vendo.core.network.dto.SalesmanOut
import com.vendo.core.network.dto.SalesmanUpdateIn
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalesmenUiState(
    val isLoading: Boolean = true,
    val salesmen: List<SalesmanOut> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isCreating: Boolean = false,
    val createError: String? = null,
    val showCreateDialog: Boolean = false,
)

/** Salesman account roster - create/deactivate/reactivate. Every action
 * here is admin-gated server-side (POST /auth/register, PATCH
 * /salesmen/{id}, GET /salesmen) - see backend's app/api/auth.py and
 * app/api/customers.py. */
@HiltViewModel
class SalesmenViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SalesmenUiState())
    val uiState: StateFlow<SalesmenUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val salesmen = api.listSalesmen(includeInactive = true)
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false, salesmen = salesmen, error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false,
                    error = e.toUserMessage("We couldn't load the salesman roster."),
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        load()
    }

    fun toggleActive(salesman: SalesmanOut) {
        viewModelScope.launch {
            try {
                val updated = api.updateSalesman(
                    salesman.login_id, SalesmanUpdateIn(is_active = !salesman.is_active),
                )
                _uiState.value = _uiState.value.copy(
                    salesmen = _uiState.value.salesmen.map {
                        if (it.login_id == updated.login_id) updated else it
                    },
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.toUserMessage("We couldn't update that account."),
                )
            }
        }
    }

    fun openCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = true, createError = null)
    }

    fun closeCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun createAccount(loginId: String, password: String, name: String, email: String?, role: String) {
        if (loginId.isBlank() || password.isBlank() || name.isBlank()) {
            _uiState.value = _uiState.value.copy(createError = "ID, password, and name are required")
            return
        }
        _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
        viewModelScope.launch {
            try {
                val created = api.register(RegisterIn(
                    login_id = loginId.trim(), password = password, name = name.trim(),
                    email = email?.trim()?.ifBlank { null }, role = role,
                ))
                _uiState.value = _uiState.value.copy(
                    isCreating = false, showCreateDialog = false,
                    salesmen = _uiState.value.salesmen + created,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createError = e.toUserMessage("We couldn't create that account."),
                )
            }
        }
    }
}
