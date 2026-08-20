package com.vendo.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class DrawerDestination(val label: String, val route: String)

/** Kept intentionally plain (spec section 15): a simple list of routes,
 * no icons, no large modern drawer chrome. */
@Composable
fun VendoDrawerContent(
    destinations: List<DrawerDestination>,
    currentRoute: String?,
    onDestinationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(modifier = modifier.fillMaxHeight()) {
        Text(
            text = "VeNdO",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        Column {
            destinations.forEach { dest ->
                NavigationDrawerItem(
                    label = { Text(dest.label) },
                    selected = dest.route == currentRoute,
                    onClick = { onDestinationClick(dest.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}
