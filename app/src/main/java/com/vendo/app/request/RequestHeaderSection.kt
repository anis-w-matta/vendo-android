package com.vendo.app.request

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.network.dto.CustomerCandidateOut

/** The review screen's header card: order type/status, customer, and
 * reference-order fields, plus the customer picker dialog it opens. */
@Composable
internal fun HeaderSection(
    request: com.vendo.core.network.dto.RequestDetail,
    selectedCustomer: CustomerCandidateOut?,
    canSelectCustomer: Boolean,
    onSelectCustomer: () -> Unit,
    editedTargetOrderNb: String?,
    onTargetOrderNbEdited: (String) -> Unit,
) {
    val isReturn = request.primary_intent == "return_order"
    val isReorder = request.primary_intent == "repeat_order" ||
        request.primary_intent == "repeat_order_adjusted"
    if (isReturn) {
        StatusBadge(text = "RETURN", tone = VendoTone.Danger)
        Spacer(Modifier.height(10.dp))
    }
    val statusPresentation = requestStatusPresentation(request.status)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = orderTypeLabel(request.primary_intent),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusBadge(text = statusPresentation.label, tone = statusPresentation.tone)
    }
    Spacer(Modifier.height(10.dp))
    val customerLabel = selectedCustomer?.customer_name ?: request.customer_name
    if (customerLabel != null) {
        LabeledRow("Customer", customerLabel)
    } else if (isReturn || isReorder) {
        // A return's/reorder's customer comes from the reference order
        // below, not a separate pick - see the target-order-nb field and
        // commit.py. Picking a customer independently here wouldn't pull
        // in the order's own lines the way correcting the order number
        // does, so the generic picker is skipped for both.
    } else if (canSelectCustomer) {
        SelectCustomerRow(onClick = onSelectCustomer)
    } else {
        LabeledRow("Customer", "Not identified")
    }
    val targetOrderNb = request.target_order_nb
    if (targetOrderNb != null) {
        LabeledRow("Reference order", targetOrderNb)
    } else if ((isReturn || isReorder) && canSelectCustomer) {
        TargetOrderNbField(
            value = editedTargetOrderNb.orEmpty(),
            onValueChange = onTargetOrderNbEdited,
            forReorder = isReorder,
        )
    }
    if (request.languages.isNotEmpty()) {
        LabeledRow("Language", request.languages.joinToString(" + ") { it.uppercase() })
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Tappable stand-in for LabeledRow when the voice pipeline couldn't
 * identify a customer - opens CustomerPickerDialog so a reviewer can pick
 * one manually instead of being stuck behind an unresolvable blocker. */
@Composable
private fun SelectCustomerRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Customer: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Select customer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Shown instead of a read-only "Reference order" row when a return
 * couldn't be matched to a sales order at intake (spec: never guess a
 * customer for a return/reorder - it's pulled from this order number
 * instead, see AcceptIn.target_order_nb / commit.py). */
@Composable
private fun TargetOrderNbField(
    value: String,
    onValueChange: (String) -> Unit,
    forReorder: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = if (forReorder) {
                "We couldn't match a sales order to repeat - enter the order number:"
            } else {
                "We couldn't match a sales order to return against - enter the order number:"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("Order number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun CustomerPickerDialog(
    candidates: List<CustomerCandidateOut>,
    onSearch: (String) -> Unit,
    onSelect: (CustomerCandidateOut) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Select customer", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search by name or number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (candidates.isEmpty()) {
                Text(
                    text = "Search for the customer by name or number above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(candidates.size) { i ->
                        val candidate = candidates[i]
                        CustomerCandidateRow(candidate = candidate, onClick = { onSelect(candidate) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCandidateRow(candidate: CustomerCandidateOut, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = candidate.customer_name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = candidate.cust_nb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
