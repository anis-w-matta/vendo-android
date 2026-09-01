package com.vendo.admin.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.AssignSalesmanIn
import com.vendo.core.network.dto.CustomerCandidateOut
import com.vendo.core.network.dto.CustomerDetailOut
import com.vendo.core.network.dto.SalesmanOut
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomersUiState(
    val query: String = "",
    val results: List<CustomerCandidateOut> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

data class CustomerDetailUiState(
    val custNb: String,
    val detail: CustomerDetailOut? = null,
    val salesmen: List<SalesmanOut> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** Browse/search every customer (an admin caller gets the whole book, not
 * just their own - same GET /customers/search the salesman app's picker
 * uses, just without the ownership narrowing that applies to a plain
 * salesman there) and reassign one to a different salesman. */
@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val api: ApiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomersUiState())
    val uiState: StateFlow<CustomersUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow<CustomerDetailUiState?>(null)
    val detailState: StateFlow<CustomerDetailUiState?> = _detailState.asStateFlow()

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        viewModelScope.launch {
            try {
                val results = api.searchCustomers(query)
                _uiState.value = _uiState.value.copy(isSearching = false, results = results)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.toUserMessage("We couldn't search customers."),
                )
            }
        }
    }

    fun openDetail(custNb: String) {
        _detailState.value = CustomerDetailUiState(custNb = custNb)
        viewModelScope.launch {
            try {
                val (detail, salesmen) = coroutineScope {
                    val d = async { api.getCustomerDetail(custNb) }
                    val s = async { api.listSalesmen() }
                    d.await() to s.await()
                }
                _detailState.value = _detailState.value?.copy(
                    detail = detail, salesmen = salesmen, isLoading = false,
                )
            } catch (e: Exception) {
                _detailState.value = _detailState.value?.copy(
                    isLoading = false,
                    error = e.toUserMessage("We couldn't load this customer."),
                )
            }
        }
    }

    fun closeDetail() {
        _detailState.value = null
    }

    /** salesmanId = null clears the assignment. */
    fun reassign(salesmanId: String?) {
        val state = _detailState.value ?: return
        _detailState.value = state.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val updated = api.assignCustomerSalesman(state.custNb, AssignSalesmanIn(salesmanId))
                _detailState.value = _detailState.value?.copy(isSaving = false, detail = updated)
            } catch (e: Exception) {
                _detailState.value = _detailState.value?.copy(
                    isSaving = false,
                    error = e.toUserMessage("We couldn't reassign this customer."),
                )
            }
        }
    }
}
