package com.example.superdialer.navigation

import com.example.superdialer.kakao.KakaoRoomScreen
import com.example.superdialer.messages.MessagesScreen
import com.example.superdialer.calllog.numberKey
import com.example.superdialer.dialer.recentCallSuggestions
import com.example.superdialer.contacts.DuplicateContactsScreen
import com.example.superdialer.contacts.ContactGroupsScreen
import com.example.superdialer.dialer.Suggestion
import com.example.superdialer.settings.QuickDialScreen
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
import com.example.superdialer.calllog.CallStatsScreen
import com.example.superdialer.settings.BlockedNumbersScreen
import com.example.superdialer.settings.BlockedNumbersViewModel
import com.example.superdialer.settings.RejectMessagesScreen
import com.example.superdialer.settings.SettingsScreen

private const val ENTRY_ID_ARG = "entryId"
private const val KAKAO_ROOM_ARG = "room"
private const val KAKAO_ROOM_ROUTE = "calllog/kakao/{$KAKAO_ROOM_ARG}"
private const val THREAD_NUMBER_ARG = "number"
private const val THREAD_ROUTE = "calllog/thread/{$THREAD_NUMBER_ARG}"
private const val HISTORY_TAB_ARG = "tab"
private const val HISTORY_TAB_MESSAGES = "messages"
private const val CALL_HISTORY_ROUTE = "calllog/{$ENTRY_ID_ARG}?$HISTORY_TAB_ARG={$HISTORY_TAB_ARG}"
private const val BLOCKED_ROUTE = "settings/blocked"
private const val CALL_STATS_ROUTE = "calllog/tools/stats"
private const val REJECT_ROUTE = "settings/reject"
private const val QUICK_DIAL_ROUTE = "settings/quickdial"
private const val CONTACT_GROUPS_ROUTE = "contacts/tools/groups"
private const val CONTACT_DUPLICATES_ROUTE = "contacts/tools/duplicates"
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

    // Shown on the keypad while no number is typed: starred contacts, then the most recent calls.
    val quickPicks by remember {
        derivedStateOf {
            val starred = contactsViewModel.contacts.filter { it.starred && it.numbers.isNotEmpty() }
                .take(10).map { Suggestion(it.name, it.numbers.first(), fromContact = true) }
            starred + recentCallSuggestions(callLogViewModel.entries, skip = starred.map { numberKey(it.number) }.toSet())
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

    // The NavHost sits inside the Scaffold, so on a cold start its graph is not set yet when these effects first run;
    // a back stack entry exists once the start screen is up, which is when navigating is safe.
    val navReady = backStackEntry != null

    // An external tel:/DIAL intent brings the keypad to the front.
    LaunchedEffect(dialerViewModel.dialRequest, navReady) {
        if (navReady && dialerViewModel.dialRequest > 0) navigateToTab(TopLevelDestination.Dialer.route)
    }

    LaunchedEffect(requestedRoute, navReady) {
        if (navReady && requestedRoute != null) {
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
                    quickPicks = quickPicks,
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
                    onOpenMessages = { id -> navController.navigate("calllog/$id?$HISTORY_TAB_ARG=$HISTORY_TAB_MESSAGES") },
                    onOpenThread = { number -> navController.navigate("calllog/thread/${android.net.Uri.encode(number)}") },
                    onOpenKakao = { room -> navController.navigate("calllog/kakao/${android.net.Uri.encode(room)}") },
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                    onOpenBlocked = { navController.navigate(BLOCKED_ROUTE) },
                    onOpenStats = { navController.navigate(CALL_STATS_ROUTE) },
                )
            }
            composable(CALL_STATS_ROUTE) {
                CallStatsScreen(callLogViewModel, onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.Settings.route) {
                SettingsScreen(
                    onOpenBlocked = { navController.navigate(BLOCKED_ROUTE) },
                    onOpenRejectMessages = { navController.navigate(REJECT_ROUTE) },
                    onOpenQuickDial = { navController.navigate(QUICK_DIAL_ROUTE) },
                )
            }
            composable(QUICK_DIAL_ROUTE) {
                QuickDialScreen(contactsViewModel, onBack = { navController.popBackStack() })
            }
            composable(BLOCKED_ROUTE) {
                BlockedNumbersScreen(blockedViewModel, onBack = { navController.popBackStack() })
            }
            composable(REJECT_ROUTE) {
                RejectMessagesScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = KAKAO_ROOM_ROUTE,
                arguments = listOf(navArgument(KAKAO_ROOM_ARG) { type = NavType.StringType }),
            ) { entry ->
                KakaoRoomScreen(
                    room = entry.arguments?.getString(KAKAO_ROOM_ARG).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = THREAD_ROUTE,
                arguments = listOf(navArgument(THREAD_NUMBER_ARG) { type = NavType.StringType }),
            ) { entry ->
                MessagesScreen(
                    number = entry.arguments?.getString(THREAD_NUMBER_ARG).orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = CALL_HISTORY_ROUTE,
                arguments = listOf(
                    navArgument(ENTRY_ID_ARG) { type = NavType.LongType },
                    navArgument(HISTORY_TAB_ARG) { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                CallHistoryScreen(
                    viewModel = callLogViewModel,
                    entryId = entry.arguments?.getLong(ENTRY_ID_ARG) ?: 0L,
                    onBack = { navController.popBackStack() },
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                    startOnMessages = entry.arguments?.getString(HISTORY_TAB_ARG) == HISTORY_TAB_MESSAGES,
                )
            }
            composable(TopLevelDestination.Contacts.route) {
                ContactsScreen(
                    viewModel = contactsViewModel,
                    onOpenContact = { id -> navController.navigate("contacts/$id") },
                    onOpenBlocked = { navController.navigate(BLOCKED_ROUTE) },
                    onOpenGroups = { navController.navigate(CONTACT_GROUPS_ROUTE) },
                    onOpenDuplicates = { navController.navigate(CONTACT_DUPLICATES_ROUTE) },
                )
            }
            composable(CONTACT_GROUPS_ROUTE) {
                ContactGroupsScreen(contactsViewModel, onBack = { navController.popBackStack() })
            }
            composable(CONTACT_DUPLICATES_ROUTE) {
                DuplicateContactsScreen(contactsViewModel, onBack = { navController.popBackStack() })
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
