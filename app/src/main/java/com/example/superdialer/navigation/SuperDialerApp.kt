package com.example.superdialer.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.superdialer.browser.BrowserScreen
import com.example.superdialer.calllog.CallHistoryScreen
import com.example.superdialer.calllog.CallLogScreen
import com.example.superdialer.calllog.CallLogViewModel
import com.example.superdialer.contacts.ContactDetailScreen
import com.example.superdialer.contacts.ContactsScreen
import com.example.superdialer.contacts.ContactsViewModel
import com.example.superdialer.dialer.DialerScreen
import com.example.superdialer.dialer.DialerViewModel
import com.example.superdialer.games.GamesScreen

private const val ENTRY_ID_ARG = "entryId"
private const val CALL_HISTORY_ROUTE = "calllog/{$ENTRY_ID_ARG}"
private const val CONTACT_ID_ARG = "contactId"
private const val CONTACT_DETAIL_ROUTE = "contacts/{$CONTACT_ID_ARG}"

@Composable
fun SuperDialerApp(
    dialerViewModel: DialerViewModel,
    requestedRoute: String? = null,
    onRouteHandled: () -> Unit = {},
) {
    // Created here (activity scope) so list state survives tab switches and is shared with detail screens.
    val callLogViewModel: CallLogViewModel = viewModel()
    val contactsViewModel: ContactsViewModel = viewModel()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // An external tel:/DIAL intent brings the keypad to the front.
    LaunchedEffect(dialerViewModel.dialRequest) {
        if (dialerViewModel.dialRequest > 0) navigateToTab(TopLevelDestination.Dialer.route)
    }

    LaunchedEffect(requestedRoute) {
        if (requestedRoute != null) {
            navigateToTab(requestedRoute)
            onRouteHandled()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    // Sub-screens such as "contacts/{id}" keep their tab highlighted.
                    val selected = currentRoute == destination.route ||
                        currentRoute?.startsWith(destination.route + "/") == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected && currentRoute != destination.route) {
                                navController.popBackStack(destination.route, inclusive = false)
                            } else {
                                navigateToTab(destination.route)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dialer.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Dialer.route) { DialerScreen(dialerViewModel) }
            composable(TopLevelDestination.CallLog.route) {
                CallLogScreen(
                    viewModel = callLogViewModel,
                    onOpenHistory = { id -> navController.navigate("calllog/$id") },
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                )
            }
            composable(
                route = CALL_HISTORY_ROUTE,
                arguments = listOf(navArgument(ENTRY_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                CallHistoryScreen(
                    viewModel = callLogViewModel,
                    entryId = entry.arguments?.getLong(ENTRY_ID_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                )
            }
            composable(TopLevelDestination.Contacts.route) {
                ContactsScreen(
                    viewModel = contactsViewModel,
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                )
            }
            composable(
                route = CONTACT_DETAIL_ROUTE,
                arguments = listOf(navArgument(CONTACT_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                ContactDetailScreen(
                    viewModel = contactsViewModel,
                    contactId = entry.arguments?.getLong(CONTACT_ID_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TopLevelDestination.Browser.route) { BrowserScreen() }
            composable(TopLevelDestination.Games.route) { GamesScreen() }
        }
    }
}
