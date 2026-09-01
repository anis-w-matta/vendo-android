package com.vendo.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.vendo.admin.navigation.AdminNavGraph
import com.vendo.core.designsystem.LocalWindowWidthSizeClass
import com.vendo.core.designsystem.VendoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(
                LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass,
            ) {
                VendoAdminApp()
            }
        }
    }
}

@Composable
private fun VendoAdminApp(appViewModel: AdminAppViewModel = hiltViewModel()) {
    val themeMode by appViewModel.themeMode.collectAsState()
    VendoTheme(mode = themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val navController = rememberNavController()
            AdminNavGraph(
                navController = navController,
                appViewModel = appViewModel,
            )
        }
    }
}
