package com.vendo.core.designsystem

import androidx.compose.ui.unit.dp

/** Named component-size tokens, extracted from what were previously
 * repeated magic-number literals in individual components - same values,
 * just given a name so a future change has one place to happen instead of
 * a grep across core/designsystem/components. */
object VendoDimens {
    val PillButtonMinWidth = 140.dp
    val CardCornerRadius = 20.dp
    val CardPadding = 20.dp
    val CardBorderWidth = 2.dp
    val ListItemHeight = 52.dp
    val ListItemCornerRadius = 12.dp
    val RecordButtonSize = 72.dp
    val RecordButtonDotSizeIdle = 28.dp
    val RecordButtonDotSizeRecording = 22.dp
}
