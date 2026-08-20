package com.vendo.core.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.VendoDarkGray
import com.vendo.core.designsystem.VendoDimens

enum class PillVariant { PrimaryBlue, DarkBlue, DarkGray }

/** Pill-shaped button, the only button shape in the VeNdO UI (spec section 21). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PillVariant = PillVariant.PrimaryBlue,
    enabled: Boolean = true,
) {
    val container = when (variant) {
        PillVariant.PrimaryBlue -> MaterialTheme.colorScheme.primary
        PillVariant.DarkBlue -> MaterialTheme.colorScheme.secondary
        PillVariant.DarkGray -> VendoDarkGray
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonColors(
            containerColor = container,
            contentColor = Color.White,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
        modifier = modifier.defaultMinSize(minWidth = VendoDimens.PillButtonMinWidth),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
