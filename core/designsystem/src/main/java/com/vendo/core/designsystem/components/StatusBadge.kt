package com.vendo.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Semantic meaning behind a badge, never conveyed by color alone - every
 * tone below pairs a color with a distinct default icon (spec: "never rely
 * on color alone" for status/uncertainty). Deliberately reuses the existing
 * reference palette's theme roles (primary/secondary/error) rather than
 * introducing new literal colors - see Color.kt. */
enum class VendoTone { Neutral, Info, Positive, Warning, Danger }

/** Small pill combining an icon and short text - the shared building block
 * for request status ("In Review"), match confidence ("Good match"/"Needs
 * review"), and issue severity, so the same visual language is used
 * everywhere uncertainty needs to be shown instead of a bare color dot. */
@Composable
fun StatusBadge(
    text: String,
    tone: VendoTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = tone.defaultIcon(),
) {
    val (container, content) = tone.colors()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.padding(end = 4.dp).size(13.dp),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@Composable
private fun VendoTone.colors(): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        VendoTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        VendoTone.Info -> scheme.secondary.copy(alpha = 0.16f) to scheme.secondary
        VendoTone.Positive -> scheme.primary.copy(alpha = 0.16f) to scheme.primary
        VendoTone.Warning -> scheme.error.copy(alpha = 0.14f) to scheme.error
        VendoTone.Danger -> scheme.error.copy(alpha = 0.20f) to scheme.error
    }
}

private fun VendoTone.defaultIcon(): ImageVector? = when (this) {
    VendoTone.Neutral -> null
    VendoTone.Info -> Icons.Filled.Info
    VendoTone.Positive -> Icons.Filled.CheckCircle
    VendoTone.Warning -> Icons.Filled.Warning
    VendoTone.Danger -> Icons.Filled.Warning
}
