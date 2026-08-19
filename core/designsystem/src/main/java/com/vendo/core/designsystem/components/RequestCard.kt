package com.vendo.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.VendoPrimaryBlue

/** Blue-bordered rounded card that hosts the Request screen's order details
 * and EDIT button (spec section 12). White/dark inner surface, always a
 * visibly blue border regardless of theme. */
@Composable
fun RequestCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, VendoPrimaryBlue, RoundedCornerShape(20.dp))
            .padding(20.dp),
        content = content,
    )
}
