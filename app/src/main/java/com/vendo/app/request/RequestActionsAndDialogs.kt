@file:OptIn(ExperimentalLayoutApi::class)

package com.vendo.app.request

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.network.dto.CandidateOut

/** Blockers that must clear before Accept is enabled, jumping the reviewer
 * to the offending line when one is tied to a specific line number. */
@Composable
internal fun BlockersCard(blockers: List<BlockingIssue>, onJump: (Int?) -> Unit) {
    RequestCard {
        Text(
            text = "${blockers.size} thing${if (blockers.size == 1) "" else "s"} need${if (blockers.size == 1) "s" else ""} attention before this order can be accepted",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blockers.forEach { issue ->
                Text(
                    text = "• ${issue.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (issue.lineNb != null) {
                                Modifier.clickable { onJump(issue.lineNb) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
internal fun ActionsRow(
    canAccept: Boolean,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(text = "Reject", variant = PillVariant.DarkGray, onClick = onReject)
            PillButton(
                text = "Accept",
                variant = PillVariant.PrimaryBlue,
                enabled = canAccept,
                onClick = onAccept,
            )
        }
        PillButton(
            text = if (isEditing) "Done editing" else "Edit",
            variant = PillVariant.DarkGray,
            onClick = onToggleEdit,
        )
    }
}

@Composable
internal fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    val reasons = listOf(
        "Customer unreachable", "Wrong customer", "Duplicate order", "Pricing issue", "Other",
    )
    var selected by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject this order") },
        text = {
            Column {
                Text(
                    "Why is this order being rejected? The reason is kept with the request.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reasons.size) { i ->
                        val reason = reasons[i]
                        FilterChip(
                            selected = selected == reason,
                            onClick = { selected = reason },
                            label = { Text(reason) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Additional detail (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onConfirm(it, note.ifBlank { null }) } },
            ) { Text("Reject") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ProductPickerDialog(
    line: EditableLine,
    onSearch: (String) -> Unit,
    onSelect: (CandidateOut) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(line.itemDesc.ifBlank { line.rawText }) }
    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Choose a product", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search catalogue") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (line.candidates.isEmpty()) {
                Text(
                    text = "Search the catalogue above, or leave this item unresolved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(line.candidates.size) { i ->
                        val candidate = line.candidates[i]
                        CandidateRow(candidate = candidate, selected = candidate.item_nb == line.itemNb, onClick = { onSelect(candidate) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) { Text("Leave unresolved") }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: CandidateOut, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.item_desc,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${candidate.item_nb} · ${candidate.category}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.attribute_conflict) {
                Text(
                    text = "Size, color, or promotion may not match what was said.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        StatusBadge(
            text = candidateQualityLabel(candidate.score),
            tone = if (candidate.attribute_conflict) VendoTone.Danger else VendoTone.Neutral,
        )
    }
}
