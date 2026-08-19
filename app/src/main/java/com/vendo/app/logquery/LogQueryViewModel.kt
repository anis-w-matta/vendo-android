package com.vendo.app.logquery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.ActivityLogOut
import com.vendo.core.network.dto.logQueryLine
import dagger.hilt.android.lifecycle.HiltViewModel
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
                val rows: List<ActivityLogOut> =
                    api.listActivity(eventType = "order_committed", limit = 100)
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
