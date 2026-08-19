package com.vendo.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Explicit LIGHT/DARK only - the spec calls for an on/off toggle the user
// controls directly, not a SYSTEM-follows-device third option.
enum class VendoThemeMode { LIGHT, DARK }

private val VendoLightColors = lightColorScheme(
    primary = VendoPrimaryBlue,
    onPrimary = VendoWhite,
    secondary = VendoDarkBlue,
    onSecondary = VendoWhite,
    background = VendoWhite,
    onBackground = VendoBlack,
    surface = VendoWhite,
    onSurface = VendoBlack,
    surfaceVariant = VendoLightGray,
    onSurfaceVariant = VendoBlack,
    outline = VendoGray,
)

private val VendoDarkColors = darkColorScheme(
    primary = VendoPrimaryBlue,
    onPrimary = VendoWhite,
    secondary = VendoDarkBlue,
    onSecondary = VendoWhite,
    background = VendoDarkBackground,
    onBackground = VendoWhite,
    surface = VendoDarkSurface,
    onSurface = VendoWhite,
    surfaceVariant = VendoDarkSurfaceAlt,
    onSurfaceVariant = VendoWhite,
    outline = VendoDarkGray,
)

@Composable
fun VendoTheme(mode: VendoThemeMode, content: @Composable () -> Unit) {
    val colors = if (mode == VendoThemeMode.DARK) VendoDarkColors else VendoLightColors
    MaterialTheme(
        colorScheme = colors,
        typography = VendoTypography,
        content = content,
    )
}
