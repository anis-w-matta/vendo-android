package com.vendo.app.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vendo.core.designsystem.VendoPrimaryBlue
import com.vendo.core.designsystem.components.MenuListItem

/** Blue-dominant background per the reference; four stacked light rows,
 * no icons/arrows/descriptions - spec section 14. */
@Composable
fun MenuScreen(
    onAccountInfo: () -> Unit,
    onLogQuery: () -> Unit,
    onChangePassword: () -> Unit,
    onLogOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VendoPrimaryBlue)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MenuListItem(text = "ACCOUNT INFO", onClick = onAccountInfo)
            MenuListItem(text = "LOG QUERY", onClick = onLogQuery)
            MenuListItem(text = "CHANGE PASSWORD", onClick = onChangePassword)
            MenuListItem(text = "LOG OUT", onClick = onLogOut)
        }
    }
}
