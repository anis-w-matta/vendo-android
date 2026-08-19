package com.vendo.app.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Shows `error` as a one-shot snackbar whenever it changes to a new
 * non-null value, instead of the old static red Text under the form -
 * auto-dismisses and doesn't shift layout. Every screen with a `String?
 * error` field in its ui state follows this same pattern. */
@Composable
fun ErrorSnackbarEffect(error: String?, hostState: SnackbarHostState) {
    LaunchedEffect(error) {
        if (error != null) hostState.showSnackbar(error)
    }
}
