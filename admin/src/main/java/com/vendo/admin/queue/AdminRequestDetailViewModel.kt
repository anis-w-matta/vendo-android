package com.vendo.admin.queue

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.admin.navigation.AdminDestinations
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.RequestDetail
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminRequestDetailUiState(
    val isLoading: Boolean = true,
    val request: RequestDetail? = null,
    val error: String? = null,
)

/** Read-only single-request view - no claim/accept/reject/edit, see
 * AdminQueueScreen's doc comment for why. */
@HiltViewModel
class AdminRequestDetailViewModel @Inject constructor(
    private val api: ApiService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val requestId: Int = checkNotNull(savedStateHandle[AdminDestinations.REQUEST_ARG])

    private val _uiState = MutableStateFlow(AdminRequestDetailUiState())
    val uiState: StateFlow<AdminRequestDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _uiState.value = AdminRequestDetailUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val request = api.getRequest(requestId)
                _uiState.value = AdminRequestDetailUiState(isLoading = false, request = request)
            } catch (e: Exception) {
                _uiState.value = AdminRequestDetailUiState(
                    isLoading = false,
                    error = e.toUserMessage("We couldn't load this request."),
                )
            }
        }
    }
}
