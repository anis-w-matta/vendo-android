package com.vendo.admin.navigation

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
import com.vendo.admin.AdminAppViewModel
import com.vendo.admin.AdminSessionState
import com.vendo.admin.activity.AdminActivityScreen
import com.vendo.admin.customers.CustomersScreen
import com.vendo.admin.login.AdminLoginScreen
import com.vendo.admin.menu.AccountScreen
import com.vendo.admin.menu.ChangePasswordScreen
import com.vendo.admin.orderhistory.AdminOrderHistoryScreen
import com.vendo.admin.queue.AdminQueueScreen
import com.vendo.admin.queue.AdminRequestDetailScreen
import com.vendo.admin.salesmen.SalesmenScreen
import com.vendo.core.designsystem.components.DrawerDestination
import com.vendo.core.designsystem.components.VendoDrawerContent
import com.vendo.core.designsystem.components.VendoTopBar
import com.vendo.core.network.AuthEvent
import kotlinx.coroutines.launch

private const val LOG_OUT_LABEL = "Log Out"

private val DRAWER_DESTINATIONS = listOf(
    DrawerDestination("Customers", AdminDestinations.CUSTOMERS),
    DrawerDestination("Salesmen", AdminDestinations.SALESMEN),
    DrawerDestination("Queue", AdminDestinations.QUEUE),
    DrawerDestination("Activity Log", AdminDestinations.ACTIVITY),
    DrawerDestination("Order History", AdminDestinations.ORDER_HISTORY),
    DrawerDestination("Account Info", AdminDestinations.ACCOUNT),
    DrawerDestination("Change Password", AdminDestinations.CHANGE_PASSWORD),
    DrawerDestination(LOG_OUT_LABEL, null),
)

@Composable
fun AdminNavGraph(
    navController: NavHostController,
    appViewModel: AdminAppViewModel,
) {
    val sessionState by appViewModel.sessionState.collectAsState()
    val themeMode by appViewModel.themeMode.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(Unit) {
        appViewModel.authEvents.collect { event ->
            if (event is AuthEvent.LoggedOut) {
                navController.navigate(AdminDestinations.LOGIN) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
        }
    }

    if (sessionState is AdminSessionState.Loading) {
        Column(modifier = Modifier.fillMaxSize()) {}
        return
    }

    val startDestination = if (sessionState is AdminSessionState.LoggedIn) {
        AdminDestinations.CUSTOMERS
    } else {
        AdminDestinations.LOGIN
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
                    when {
                        route != null -> navController.navigate(route) { launchSingleTop = true }
                        dest.label == LOG_OUT_LABEL -> {
                            appViewModel.logOut()
                            navController.navigate(AdminDestinations.LOGIN) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    }
                },
            )
        },
        gesturesEnabled = currentRoute != AdminDestinations.LOGIN,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentRoute != AdminDestinations.LOGIN && currentRoute != null) {
                VendoTopBar(
                    themeMode = themeMode,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onThemeToggle = appViewModel::toggleTheme,
                    title = "VeNdO Admin",
                )
            }

            NavHost(navController = navController, startDestination = startDestination) {
                composable(AdminDestinations.LOGIN) {
                    AdminLoginScreen(onLoginSuccess = {
                        navController.navigate(AdminDestinations.CUSTOMERS) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    })
                }
                composable(AdminDestinations.CUSTOMERS) {
                    CustomersScreen()
                }
                composable(AdminDestinations.SALESMEN) {
                    SalesmenScreen()
                }
                composable(AdminDestinations.QUEUE) {
                    AdminQueueScreen(onOpenRequest = { requestId ->
                        navController.navigate(AdminDestinations.requestRoute(requestId))
                    })
                }
                composable(
                    route = AdminDestinations.REQUEST,
                    arguments = listOf(navArgument(AdminDestinations.REQUEST_ARG) { type = NavType.IntType }),
                ) {
                    AdminRequestDetailScreen()
                }
                composable(AdminDestinations.ACTIVITY) {
                    AdminActivityScreen(onOpenRequest = { requestId ->
                        navController.navigate(AdminDestinations.requestRoute(requestId))
                    })
                }
                composable(AdminDestinations.ORDER_HISTORY) {
                    AdminOrderHistoryScreen()
                }
                composable(AdminDestinations.ACCOUNT) {
                    AccountScreen()
                }
                composable(AdminDestinations.CHANGE_PASSWORD) {
                    ChangePasswordScreen(onDone = { navController.popBackStack() })
                }
            }
        }
    }
}
