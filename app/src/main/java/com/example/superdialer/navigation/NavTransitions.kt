package com.example.superdialer.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Screen transitions: tabs slide sideways in the direction of the tab order, sub-screens (contact detail,
 * call history, blocked numbers, ...) slide in from the right over their parent and back out to the right,
 * with a slight parallax on the screen underneath.
 */
internal object NavTransitions {
    private const val MS = 320
    private val easing = FastOutSlowInEasing

    private fun tabIndex(route: String?): Int =
        TopLevelDestination.entries.indexOfFirst { route == it.route || route?.startsWith(it.route + "/") == true }

    private fun isTab(route: String?) = TopLevelDestination.entries.any { it.route == route }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.route(entry: NavBackStackEntry) = entry.destination.route

    /** Moving between two tabs: +1 when the target tab is to the right. */
    private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabDirection(): Int =
        Integer.signum(tabIndex(route(targetState)) - tabIndex(route(initialState)))

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition = with(scope) {
        val from = route(initialState)
        val to = route(targetState)
        if (isTab(from) && isTab(to)) {
            val dir = tabDirection().let { if (it == 0) 1 else it }
            slideInHorizontally(tween(MS, easing = easing)) { dir * it / 5 } + fadeIn(tween(MS, easing = easing))
        } else {
            slideInHorizontally(tween(MS, easing = easing)) { it }
        }
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition = with(scope) {
        val from = route(initialState)
        val to = route(targetState)
        if (isTab(from) && isTab(to)) {
            val dir = tabDirection().let { if (it == 0) 1 else it }
            slideOutHorizontally(tween(MS, easing = easing)) { -dir * it / 5 } + fadeOut(tween(MS / 2, easing = easing))
        } else {
            slideOutHorizontally(tween(MS, easing = easing)) { -it / 4 } + fadeOut(tween(MS, easing = easing))
        }
    }

    fun popEnter(scope: AnimatedContentTransitionScope<NavBackStackEntry>): EnterTransition =
        slideInHorizontally(tween(MS, easing = easing)) { -it / 4 } + fadeIn(tween(MS, easing = easing))

    fun popExit(scope: AnimatedContentTransitionScope<NavBackStackEntry>): ExitTransition =
        slideOutHorizontally(tween(MS, easing = easing)) { it }
}
