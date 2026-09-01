package com.vendo.admin.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Shows `error` as a one-shot snackbar whenever it changes to a new
 * non-null value - mirrors :app's identical helper (com.vendo.app.common)
 * since screens aren't shared between the two apps. */
@Composable
fun ErrorSnackbarEffect(error: String?, hostState: SnackbarHostState) {
    LaunchedEffect(error) {
        if (error != null) hostState.showSnackbar(error)
    }
}
