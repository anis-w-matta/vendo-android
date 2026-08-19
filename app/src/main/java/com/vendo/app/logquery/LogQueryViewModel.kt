package com.vendo.app.logquery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.ActivityLogOut
import com.vendo.core.network.dto.LOG_QUERY_EVENT_TYPES
import com.vendo.core.network.dto.isDraftSubmission
import com.vendo.core.network.dto.logQueryLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogQueryUiState(
    val isLoading: Boolean = true,
    val lines: List<String> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LogQueryViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogQueryUiState())
    val uiState: StateFlow<LogQueryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // No single-request "event_type in (...)" filter on the
                // backend (GET /activity only takes one exact event_type) -
                // fetch LOG_QUERY_EVENT_TYPES in parallel and merge instead.
                val rows: List<ActivityLogOut> = coroutineScope {
                    LOG_QUERY_EVENT_TYPES
                        .map { type -> async { api.listActivity(eventType = type, limit = 100) } }
                        .map { it.await() }
                        .flatten()
                }
                    .filter { it.event_type != "voice_received" || it.isDraftSubmission() }
                    .sortedByDescending { it.ts }
                _uiState.value = LogQueryUiState(
                    isLoading = false,
                    lines = rows.map { it.logQueryLine() },
                )
            } catch (e: Exception) {
                _uiState.value = LogQueryUiState(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }
}
