package com.vendo.admin.orderhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.RecentOrderOut
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminOrderHistoryUiState(
    val isLoading: Boolean = true,
    val orders: List<RecentOrderOut> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/** Same visual/interaction pattern as :app's OrderHistoryScreen, but a
 * direct GET /orders/recent call each time rather than reading an offline
 * Room cache - this app has no offline-cache infrastructure (see
 * AdminAppViewModel's doc comment), every screen here is a live read. */
@HiltViewModel
class AdminOrderHistoryViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminOrderHistoryUiState())
    val uiState: StateFlow<AdminOrderHistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val orders = api.listRecentOrders(limit = 100)
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false, orders = orders, error = null,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, isRefreshing = false,
                    error = e.toUserMessage("We couldn't load order history."),
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        load()
    }
}
