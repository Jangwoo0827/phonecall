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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.superdialer.browser.BrowserScreen
import com.example.superdialer.calllog.CallLogScreen
import com.example.superdialer.contacts.ContactsScreen
import com.example.superdialer.dialer.DialerScreen
import com.example.superdialer.dialer.DialerViewModel
import com.example.superdialer.games.GamesScreen

@Composable
fun SuperDialerApp(dialerViewModel: DialerViewModel) {
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navigateToTab(destination.route) },
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
            composable(TopLevelDestination.CallLog.route) { CallLogScreen() }
            composable(TopLevelDestination.Contacts.route) { ContactsScreen() }
            composable(TopLevelDestination.Browser.route) { BrowserScreen() }
            composable(TopLevelDestination.Games.route) { GamesScreen() }
        }
    }
}
