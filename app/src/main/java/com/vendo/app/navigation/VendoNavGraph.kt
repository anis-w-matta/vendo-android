package com.vendo.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.vendo.app.AppViewModel
import com.vendo.app.SessionState
import com.vendo.app.login.LoginScreen
import com.vendo.app.logquery.LogQueryScreen
import com.vendo.app.menu.AccountScreen
import com.vendo.app.menu.ChangePasswordScreen
import com.vendo.app.record.RecordScreen
import com.vendo.app.request.RequestScreen
import com.vendo.core.designsystem.components.DrawerDestination
import com.vendo.core.designsystem.components.VendoDrawerContent
import com.vendo.core.designsystem.components.VendoTopBar
import com.vendo.core.network.AuthEvent
import kotlinx.coroutines.launch

private val DRAWER_DESTINATIONS = listOf(
    DrawerDestination("Record", VendoDestinations.RECORD),
    DrawerDestination("Request", VendoDestinations.requestRouteDefault()),
    DrawerDestination("Log Query", VendoDestinations.LOG_QUERY),
    DrawerDestination("Account Info", VendoDestinations.ACCOUNT),
    DrawerDestination("Change Password", VendoDestinations.CHANGE_PASSWORD),
    // route null: an action, not a screen - VendoNavGraph's
    // onDestinationClick logs out instead of navigating.
    DrawerDestination("Log Out", null),
)

@Composable
fun VendoNavGraph(
    navController: NavHostController,
    appViewModel: AppViewModel,
) {
    val sessionState by appViewModel.sessionState.collectAsState()
    val themeMode by appViewModel.themeMode.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // ModalNavigationDrawer doesn't consume system back on its own - without
    // this, back-while-open falls through to the Activity and exits the app
    // instead of just closing the drawer.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(Unit) {
        appViewModel.authEvents.collect { event ->
            if (event is AuthEvent.LoggedOut) {
                navController.navigate(VendoDestinations.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    if (sessionState is SessionState.Loading) {
        // Deliberately blank, not a designed splash screen - just a brief
        // gate while DataStore's first emission arrives (see AppViewModel).
        Column(modifier = Modifier.fillMaxSize()) {}
        return
    }

    val startDestination = if (sessionState is SessionState.LoggedIn) {
        VendoDestinations.RECORD
    } else {
        VendoDestinations.LOGIN
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            VendoDrawerContent(
                destinations = DRAWER_DESTINATIONS,
                currentRoute = currentRoute,
                onDestinationClick = { dest ->
                    scope.launch { drawerState.close() }
                    val route = dest.route
                    if (route != null) {
                        navController.navigate(route) { launchSingleTop = true }
                    } else {
                        appViewModel.logOut()
                        navController.navigate(VendoDestinations.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
            )
        },
        gesturesEnabled = currentRoute != VendoDestinations.LOGIN,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentRoute != VendoDestinations.LOGIN && currentRoute != null) {
                VendoTopBar(
                    themeMode = themeMode,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onThemeToggle = appViewModel::toggleTheme,
                )
            }

            NavHost(navController = navController, startDestination = startDestination) {
                composable(VendoDestinations.LOGIN) {
                    LoginScreen(onLoginSuccess = {
                        navController.navigate(VendoDestinations.RECORD) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    })
                }
                composable(VendoDestinations.RECORD) {
                    RecordScreen(onOpenRequest = { requestId ->
                        navController.navigate(VendoDestinations.requestRoute(requestId))
                    })
                }
                composable(
                    route = VendoDestinations.REQUEST,
                    arguments = listOf(
                        navArgument(VendoDestinations.REQUEST_ARG) {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                ) {
                    RequestScreen(
                        onAccepted = {
                            navController.navigate(VendoDestinations.LOG_QUERY) {
                                popUpTo(VendoDestinations.RECORD)
                            }
                        },
                        onRejected = {
                            navController.popBackStack(VendoDestinations.RECORD, inclusive = false)
                        },
                        onCalledBack = {
                            navController.popBackStack(VendoDestinations.RECORD, inclusive = false)
                        },
                    )
                }
                composable(VendoDestinations.LOG_QUERY) {
                    LogQueryScreen(
                        onOpenRequest = { requestId ->
                            navController.navigate(VendoDestinations.requestRoute(requestId))
                        },
                    )
                }
                composable(VendoDestinations.ACCOUNT) {
                    AccountScreen()
                }
                composable(VendoDestinations.CHANGE_PASSWORD) {
                    ChangePasswordScreen(onDone = { navController.popBackStack() })
                }
            }
        }
    }
}
