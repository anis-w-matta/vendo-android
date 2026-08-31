@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.vendo.app.request

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.components.QuantityStepper
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone

/** The review screen's "ITEMS" card: the editable line list, plus the
 * read-only QRA bonus lines a customer's promotional agreement adds at
 * commit time (see backend's preview_qra - those have no PendingLine of
 * their own yet, so they're never editable/deletable here). */
@Composable
internal fun ItemsSection(
    state: RequestUiState,
    viewModel: RequestViewModel,
    lineRequesters: MutableMap<Int, BringIntoViewRequester>,
) {
    Text(text = "ITEMS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    if (state.editableLines.isEmpty()) {
        Text(
            text = "No items were understood from this recording.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.editableLines.forEach { line ->
        val requester = lineRequesters.getOrPut(line.lineNb) { BringIntoViewRequester() }
        Box(modifier = Modifier.bringIntoViewRequester(requester)) {
            ItemRow(
                line = line,
                isEditing = state.isEditing && !state.isReadOnly,
                onQtyChange = { viewModel.updateLine(line.lineNb, qty = it) },
                onUomChange = { viewModel.updateLine(line.lineNb, uom = it) },
                onChangeProduct = { viewModel.openProductPicker(line.lineNb) },
                onDelete = { viewModel.deleteLine(line.lineNb) },
                onUndoDelete = { viewModel.undoDelete(line.lineNb) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    // QRA bonus lines have no PendingLine of their own yet (see backend's
    // preview_qra) - shown read-only, never editable/deletable like a
    // real line, since Accept doesn't send them at all: the server adds
    // the real one itself inside commit().
    state.request?.qra_bonus_lines?.forEach { bonus ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = bonus.item_desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(text = "FREE (QRA bonus)", tone = VendoTone.Positive)
            }
            Text(
                text = "${bonus.qty} ${bonus.uom.orEmpty()}".trim(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    if (state.isEditing && !state.isReadOnly) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PillButton(text = "+ ADD ITEM", variant = PillVariant.DarkGray, onClick = viewModel::addLine)
        }
    }
}

@Composable
private fun ItemRow(
    line: EditableLine,
    isEditing: Boolean,
    onQtyChange: (String) -> Unit,
    onUomChange: (String) -> Unit,
    onChangeProduct: () -> Unit,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
) {
    if (line.isRemoved) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\"${line.displayLabel()}\" removed",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onUndoDelete) { Text("UNDO") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = line.displayLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isEditing) {
                QuantityStepper(value = line.qty, onValueChange = onQtyChange)
            } else {
                Text(
                    text = "${line.qty.ifBlank { "?" }} ${line.uom}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (line.rawText.isNotBlank() && line.rawText != line.itemDesc) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Spoken: “${line.rawText}”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (label, tone) = when (line.matchStatus) {
                LineMatchStatus.CONFIRMED -> "Confirmed" to VendoTone.Positive
                LineMatchStatus.NEEDS_REVIEW -> "Needs review" to VendoTone.Warning
                LineMatchStatus.CONFLICT -> "Doesn't match" to VendoTone.Danger
                LineMatchStatus.UNRESOLVED -> "Unresolved" to VendoTone.Danger
            }
            StatusBadge(text = label, tone = tone)
            changeLabel(line.change)?.let { StatusBadge(text = it, tone = VendoTone.Info) }
            line.qraSubstitutedItemDesc?.let {
                StatusBadge(text = "QRA: becomes \"$it\"", tone = VendoTone.Info)
            }
            line.qraUnitPrice?.let {
                StatusBadge(text = "QRA price: $it", tone = VendoTone.Info)
            }
            if (line.matchStatus == LineMatchStatus.CONFIRMED) {
                matchMethodLabel(line.matchMethod)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (line.matchStatus == LineMatchStatus.CONFLICT) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The spoken size, color, or promotion doesn't match this product.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        line.lineFlags.forEach { flag ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = lineFlagMessage(flag),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (isEditing) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(text = "CHANGE PRODUCT", variant = PillVariant.DarkGray, onClick = onChangeProduct)
                PillButton(text = "DELETE", variant = PillVariant.DarkGray, onClick = onDelete)
            }
            Spacer(Modifier.height(8.dp))
            UomSelector(value = line.uom, onValueChange = onUomChange)
        } else if (line.matchStatus != LineMatchStatus.CONFIRMED) {
            Spacer(Modifier.height(4.dp))
            PillButton(text = "REVIEW", variant = PillVariant.DarkGray, onClick = onChangeProduct)
        }
    }
}

/** The business only orders in two units (see backend's UOM_SYNONYMS
 * docstring) - a fixed Each/Packet choice instead of free text keeps a
 * reviewer's edit from drifting into a value nothing downstream recognizes,
 * and pairs with RequestUiState.blockingIssues refusing to accept a line
 * with no unit chosen at all. */
@Composable
private fun UomSelector(value: String, onValueChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("EACH" to "Each", "PKT" to "Packet").forEach { (code, label) ->
            FilterChip(
                selected = value.equals(code, ignoreCase = true),
                onClick = { onValueChange(code) },
                label = { Text(label) },
            )
        }
    }
}
