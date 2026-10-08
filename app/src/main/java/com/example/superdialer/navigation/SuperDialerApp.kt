package com.example.superdialer.navigation

import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import com.example.superdialer.dialer.findSuggestions
import com.example.superdialer.ui.hasPermission
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
import com.example.superdialer.browser.BrowserViewModel
import com.example.superdialer.calllog.CallHistoryScreen
import com.example.superdialer.calllog.CallLogScreen
import com.example.superdialer.calllog.CallLogViewModel
import com.example.superdialer.contacts.ContactDetailScreen
import com.example.superdialer.contacts.ContactsScreen
import com.example.superdialer.contacts.ContactsViewModel
import com.example.superdialer.dialer.DialerScreen
import com.example.superdialer.dialer.DialerViewModel
import com.example.superdialer.hub.WebGamesScreen
import com.example.superdialer.settings.BlockedNumbersScreen
import com.example.superdialer.settings.BlockedNumbersViewModel
import com.example.superdialer.settings.RejectMessagesScreen
import com.example.superdialer.settings.SettingsScreen

private const val ENTRY_ID_ARG = "entryId"
private const val CALL_HISTORY_ROUTE = "calllog/{$ENTRY_ID_ARG}"
private const val BLOCKED_ROUTE = "settings/blocked"
private const val REJECT_ROUTE = "settings/reject"
private const val CONTACT_ID_ARG = "contactId"
private const val CONTACT_DETAIL_ROUTE = "contacts/{$CONTACT_ID_ARG}"

@Composable
fun SuperDialerApp(
    dialerViewModel: DialerViewModel,
    browserViewModel: BrowserViewModel,
    requestedRoute: String? = null,
    onRouteHandled: () -> Unit = {},
) {
    // Created here (activity scope) so list state survives tab switches and is shared with detail screens.
    val callLogViewModel: CallLogViewModel = viewModel()
    val contactsViewModel: ContactsViewModel = viewModel()
    val blockedViewModel: BlockedNumbersViewModel = viewModel()

    val context = LocalContext.current
    // Keypad autocomplete: contacts first, then recent unsaved callers.
    val suggestions by remember {
        derivedStateOf {
            findSuggestions(dialerViewModel.number, contactsViewModel.contacts, callLogViewModel.entries)
        }
    }

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

    // A website open in the browser takes the whole screen, so the app's own tab bar steps aside.
    val hideBottomBar = currentRoute == TopLevelDestination.Hub.route && browserViewModel.immersive

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !hideBottomBar,
                enter = slideInVertically(tween(260)) { it } + fadeIn(tween(260)),
                exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(220)),
            ) { NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TopLevelDestination.entries.forEach { destination ->
                    // Sub-screens such as "contacts/{id}" keep their tab highlighted.
                    val selected = currentRoute == destination.route ||
                        currentRoute?.startsWith(destination.route + "/") == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected && currentRoute != destination.route) {
                                if (!navController.popBackStack(destination.route, inclusive = false)) {
                                    navigateToTab(destination.route)
                                }
                            } else {
                                navigateToTab(destination.route)
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            } }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Dialer.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { NavTransitions.enter(this) },
            exitTransition = { NavTransitions.exit(this) },
            popEnterTransition = { NavTransitions.popEnter(this) },
            popExitTransition = { NavTransitions.popExit(this) },
        ) {
            composable(TopLevelDestination.Dialer.route) {
                DialerScreen(
                    viewModel = dialerViewModel,
                    suggestions = suggestions,
                    onRefreshSources = {
                        if (context.hasPermission(Manifest.permission.READ_CONTACTS)) contactsViewModel.refresh()
                        if (context.hasPermission(Manifest.permission.READ_CALL_LOG)) callLogViewModel.refresh()
                    },
                )
            }
            composable(TopLevelDestination.CallLog.route) {
                CallLogScreen(
                    viewModel = callLogViewModel,
                    onOpenHistory = { id -> navController.navigate("calllog/$id") },
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                    onOpenBlocked = { navController.navigate(BLOCKED_ROUTE) },
                )
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    onOpenBlocked = { navController.navigate(BLOCKED_ROUTE) },
                    onOpenRejectMessages = { navController.navigate(REJECT_ROUTE) },
                )
            }
            composable(BLOCKED_ROUTE) {
                BlockedNumbersScreen(blockedViewModel, onBack = { navController.popBackStack() })
            }
            composable(REJECT_ROUTE) {
                RejectMessagesScreen(onBack = { navController.popBackStack() })
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
                    onOpenBlocked = { navController.navigate(BLOCKED_ROUTE) },
                )
            }
            composable(
                route = CONTACT_DETAIL_ROUTE,
                arguments = listOf(navArgument(CONTACT_ID_ARG) { type = NavType.LongType }),
            ) { entry ->
                ContactDetailScreen(
                    viewModel = contactsViewModel,
                    callLogViewModel = callLogViewModel,
                    contactId = entry.arguments?.getLong(CONTACT_ID_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(TopLevelDestination.Hub.route) { WebGamesScreen(browserViewModel) }
        }
    }
}
