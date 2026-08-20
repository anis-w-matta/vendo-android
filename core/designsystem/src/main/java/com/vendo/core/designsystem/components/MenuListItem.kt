package com.vendo.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.VendoBlack
import com.vendo.core.designsystem.VendoDimens
import com.vendo.core.designsystem.VendoWhite

/** Stacked light rounded row used on the MENU screen (ACCOUNT INFO, LOG
 * QUERY, CHANGE PASSWORD, LOG OUT) - spec section 14, no icons/arrows. */
@Composable
fun MenuListItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(VendoDimens.ListItemHeight)
            .clip(RoundedCornerShape(VendoDimens.ListItemCornerRadius))
            .background(VendoWhite)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            // The row itself is always a light surface (white/light-gray)
            // regardless of app theme, since it sits on the blue-dominant
            // MENU background either way - so its text always stays dark.
            color = VendoBlack,
        )
    }
}
