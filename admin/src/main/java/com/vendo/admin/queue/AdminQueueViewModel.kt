package com.vendo.admin.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.QueueRow
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminQueueUiState(
    val isLoading: Boolean = true,
    val rows: List<QueueRow> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/** Full pending-request queue, every salesman's - GET /queue is
 * unfiltered for an admin caller (see backend's app/api/queue.py), the
 * same way it already is when an admin logs into :app. This screen is
 * read-only: no claim/accept/reject here, see AdminRequestDetailScreen. */
@HiltViewModel
class AdminQueueViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminQueueUiState())
    val uiState: StateFlow<AdminQueueUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val rows = api.listQueue(limit = 100)
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false, rows = rows, error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false,
                    error = e.toUserMessage("We couldn't load the queue."),
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        load()
    }
}
