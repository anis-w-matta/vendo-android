package com.vendo.admin.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.ActivityLogOut
import com.vendo.core.network.dto.LOG_QUERY_EVENT_TYPES
import com.vendo.core.network.dto.LogQueryLine
import com.vendo.core.network.dto.isDraftSubmission
import com.vendo.core.network.dto.toLogQueryLine
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminActivityUiState(
    val isLoading: Boolean = true,
    val lines: List<LogQueryLine> = emptyList(),
    val error: String? = null,
)

/** Same shape as :app's LogQueryViewModel - GET /activity is already
 * unfiltered for any authenticated caller (a pre-existing gap, not
 * something this feature changes - see the plan doc), so this shows
 * every salesman's activity, not just this admin's own. */
@HiltViewModel
class AdminActivityViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminActivityUiState())
    val uiState: StateFlow<AdminActivityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val rows: List<ActivityLogOut> = coroutineScope {
                    LOG_QUERY_EVENT_TYPES
                        .map { type -> async { api.listActivity(eventType = type, limit = 100) } }
                        .map { it.await() }
                        .flatten()
                }
                    .filter { it.event_type != "voice_received" || it.isDraftSubmission() }
                    .sortedByDescending { it.ts }
                _uiState.value = AdminActivityUiState(
                    isLoading = false,
                    lines = rows.map { it.toLogQueryLine() },
                )
            } catch (e: Exception) {
                _uiState.value = AdminActivityUiState(
                    isLoading = false,
                    error = e.toUserMessage("We couldn't load the activity log."),
                )
            }
        }
    }
}
