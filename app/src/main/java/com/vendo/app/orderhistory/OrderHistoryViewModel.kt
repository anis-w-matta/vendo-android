package com.vendo.app.orderhistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.app.cache.CacheRepository
import com.vendo.core.datastore.cache.CachedOrderEntity
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrderHistoryUiState(
    val isLoading: Boolean = true,
    val orders: List<CachedOrderEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

/** Backs the Order History screen - the offline cache's `cached_orders`
 * table already mirrors the operator's own most recent committed orders
 * (with lines) for the reorder flow (see CacheRepository), so this reads
 * that same cache rather than adding a second network round trip. Nothing
 * here is ever stale by more than the operator's last Refresh, same as
 * every other cache-backed list in the app. */
@HiltViewModel
class OrderHistoryViewModel @Inject constructor(
    private val cache: CacheRepository,
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<OrderHistoryUiState> = combine(
        cache.observeOrders(), _isRefreshing, _error,
    ) { orders, isRefreshing, error ->
        OrderHistoryUiState(isLoading = false, orders = orders,
                            isRefreshing = isRefreshing, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderHistoryUiState())

    /** Re-syncs the whole offline cache (customers/items/orders/QRA), the
     * same CacheRepository.refresh() the drawer's "Refresh Data" action
     * uses - this screen's pull-to-refresh isn't a narrower, second
     * refresh path, just another way to trigger the one that already
     * exists. */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            cache.refresh().onFailure { e -> _error.value = e.toUserMessage() }
            _isRefreshing.value = false
        }
    }
}
