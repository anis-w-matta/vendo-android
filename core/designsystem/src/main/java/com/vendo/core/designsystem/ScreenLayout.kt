package com.vendo.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Set once in MainActivity from calculateWindowSizeClass(activity) so any
 * screen can branch on available width without each computing its own. */
val LocalWindowWidthSizeClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/** Screens wider than this stop growing and center instead - a phone-width
 * column stretched edge-to-edge across a tablet reads as unfinished, not
 * "responsive". */
val VendoContentMaxWidth = 640.dp

private val ScreenHorizontalCompact = 24.dp
private val ScreenHorizontalWide = 48.dp
private val ScreenTop = 32.dp
private val ScreenBottom = 24.dp

@Composable
private fun screenHorizontalPadding(): Dp =
    if (LocalWindowWidthSizeClass.current == WindowWidthSizeClass.Compact) {
        ScreenHorizontalCompact
    } else {
        ScreenHorizontalWide
    }

/**
 * Standard screen content padding: horizontal spacing widens on
 * medium/expanded windows (tablets, landscape) instead of staying a fixed
 * phone-width 24dp, and safe-drawing insets (gesture nav, display cutouts)
 * are merged into the sides/bottom so content clears them now that the app
 * draws edge-to-edge (see MainActivity's enableEdgeToEdge()).
 *
 * The top inset is deliberately excluded by default: screens hosted under
 * VendoTopBar already sit below the status bar because the top bar itself
 * reserves that space - adding it again here would double-pad. Screens with
 * no top bar (only Login) should pass [includeTopInset] = true.
 */
@Composable
fun vendoScreenPadding(includeTopInset: Boolean = false): PaddingValues {
    val horizontal = screenHorizontalPadding()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = horizontal + insets.calculateStartPadding(direction),
        end = horizontal + insets.calculateEndPadding(direction),
        top = ScreenTop + if (includeTopInset) insets.calculateTopPadding() else 0.dp,
        bottom = ScreenBottom + insets.calculateBottomPadding(),
    )
}

/** Caps a screen's content column at [VendoContentMaxWidth] - pair with a
 * parent Box(contentAlignment = Alignment.TopCenter) so it centers instead
 * of hugging one side once the window is wider than the cap. */
fun Modifier.vendoContentMaxWidth(): Modifier = this.widthIn(max = VendoContentMaxWidth)
