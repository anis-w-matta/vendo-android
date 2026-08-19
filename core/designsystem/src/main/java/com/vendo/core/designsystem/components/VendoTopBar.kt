package com.vendo.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.VendoThemeMode

private val TopBarHeight = 56.dp

/**
 * Hamburger (left) / "VeNdO" (genuinely centered on the bar, not just
 * between the two side controls) / theme toggle (right). Same on every
 * internal screen - see the VeNdO UI spec, section 8 and 37.
 */
@Composable
fun VendoTopBar(
    themeMode: VendoThemeMode,
    onMenuClick: () -> Unit,
    onThemeToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onMenuClick)
                .semantics { contentDescription = "Menu" },
            contentAlignment = Alignment.Center,
        ) {
            HamburgerIcon(tint = MaterialTheme.colorScheme.onSurface)
        }

        Text(
            text = "VeNdO",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
        ) {
            ThemeToggle(mode = themeMode, onToggle = onThemeToggle)
        }
    }
}

@Composable
private fun HamburgerIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val lineY = listOf(size.height * 0.22f, size.height * 0.5f, size.height * 0.78f)
        lineY.forEach { y ->
            drawLine(
                color = tint,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ThemeToggle(
    mode: VendoThemeMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = mode == VendoThemeMode.DARK
    val trackColor = MaterialTheme.colorScheme.outline
    val thumbColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(trackColor.copy(alpha = 0.4f))
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            contentAlignment = if (isDark) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isDark) "☽" else "☀", // moon / sun
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
