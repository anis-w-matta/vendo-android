package com.vendo.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.VendoDarkGray
import com.vendo.core.designsystem.VendoWhite
import java.math.BigDecimal

/** Fast +/- quantity editing (spec: "extremely fast", inline, "-20+" style)
 * with the number itself still directly typeable for a value further from
 * the current one than a rep wants to tap through. `value` is kept as the
 * raw string the caller already threads through to the backend (Decimal on
 * the wire) - this component only ever produces a plain non-negative
 * integer-or-decimal string, never invalid text, but does not itself decide
 * whether e.g. "0" is an acceptable final quantity - that's business rule
 * territory the caller (accept-safety validation) owns. */
@Composable
fun QuantityStepper(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    step: BigDecimal = BigDecimal.ONE,
) {
    fun current(): BigDecimal = value.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
    fun apply(next: BigDecimal) {
        val clamped = if (next < BigDecimal.ZERO) BigDecimal.ZERO else next
        onValueChange(clamped.stripTrailingZeros().toPlainString())
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(symbol = "-", enabled = enabled, onClick = { apply(current() - step) })
        BasicTextField(
            value = value,
            onValueChange = { new ->
                if (new.isEmpty() || new.matches(Regex("^\\d*\\.?\\d*$"))) onValueChange(new)
            },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(48.dp),
        )
        StepButton(symbol = "+", enabled = enabled, onClick = { apply(current() + step) })
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) VendoDarkGray else VendoDarkGray.copy(alpha = 0.4f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleMedium, color = VendoWhite)
    }
}
