package com.example.superdialer.hub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.browser.BrowserScreen
import com.example.superdialer.browser.BrowserViewModel
import com.example.superdialer.games.GamesScreen
import com.example.superdialer.i18n.str
import com.example.superdialer.maps.MapScreen
import com.example.superdialer.music.ui.MusicScreen
import com.example.superdialer.notes.NotesScreen

/** The sections of the Apps tab, in [BrowserViewModel.hubSection] order. The first [TOP_COUNT] are tabs; all are in the drawer. */
internal class HubSection(val label: () -> String, val icon: ImageVector)

internal val hubSections = listOf(
    HubSection({ str(R.string.shortcut_browser) }, Icons.Filled.Language),
    HubSection({ str(R.string.shortcut_games) }, Icons.Filled.SportsEsports),
    HubSection({ str(R.string.music_title) }, Icons.Filled.MusicNote),
    HubSection({ str(R.string.notes_title) }, Icons.Filled.EditNote),
    HubSection({ str(R.string.map_title) }, Icons.Filled.Map),
)

/** How many sections have a tab in the top row; the rest are reached through the arrow's drawer. */
internal const val TOP_COUNT = 3

/** Index of the Map section (see [BrowserViewModel.hubSection]). */
const val HUB_MAP = 4

/**
 * The "Apps" bottom tab: the built-in browser, games, music, notepad and map. The top row keeps the first few as tabs; the
 * arrow under it slides down a drawer with every app, so the row does not have to grow with the number of apps. While a
 * website is open the whole header is hidden (and the app's bottom bar too) so the page gets the screen.
 */
@Composable
fun WebGamesScreen(browserViewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    val selected = browserViewModel.hubSection.coerceIn(0, hubSections.size - 1)
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    // A link opened from another app always lands in the browser section.
    LaunchedEffect(browserViewModel.externalUrl) {
        if (browserViewModel.externalUrl != null) browserViewModel.hubSection = 0
    }
    LaunchedEffect(browserViewModel.immersive) {
        if (browserViewModel.immersive) drawerOpen = false
    }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = !browserViewModel.immersive,
            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(220)),
        ) {
            Column {
                Row(Modifier.fillMaxWidth()) {
                    hubSections.take(TOP_COUNT).forEachIndexed { index, section ->
                        val color = MaterialTheme.colorScheme.primary
                        Tab(
                            selected = selected == index,
                            onClick = { browserViewModel.hubSection = index; drawerOpen = false },
                            modifier = Modifier.weight(1f).drawBehind {
                                if (selected == index) {
                                    val w = size.width * 0.6f
                                    val x = (size.width - w) / 2
                                    val y = size.height - 1.5.dp.toPx()
                                    drawLine(color, Offset(x, y), Offset(x + w, y), strokeWidth = 3.dp.toPx())
                                }
                            },
                            text = { Text(section.label()) },
                        )
                    }
                }
                DrawerHandle(
                    open = drawerOpen,
                    currentExtra = if (selected >= TOP_COUNT) hubSections[selected] else null,
                    onClick = { drawerOpen = !drawerOpen },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(300)) { dir * it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(300)) { -dir * it / 3 } + fadeOut(tween(200)))
                },
                label = "hubSection",
            ) { section ->
                when (section) {
                    0 -> BrowserScreen(browserViewModel)
                    1 -> GamesScreen()
                    2 -> MusicScreen()
                    3 -> NotesScreen()
                    else -> MapScreen()
                }
            }
            DrawerOverlay(open = drawerOpen, selected = selected, onPick = { browserViewModel.hubSection = it; drawerOpen = false }, onClose = { drawerOpen = false })
        }
    }
}

/** The scrim and the drawer sliding down over the page (a separate function so the animations use the Box scope). */
@Composable
private fun BoxScope.DrawerOverlay(open: Boolean, selected: Int, onPick: (Int) -> Unit, onClose: () -> Unit) {
    // The scrim under the drawer: tapping the page below closes it.
    AnimatedVisibility(visible = open, enter = fadeIn(tween(200)), exit = fadeOut(tween(160))) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).clickable { onClose() })
    }
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(160)),
    ) {
        AppDrawer(selected = selected, onPick = onPick)
    }
}

/** The thin strip under the tab row: the arrow that opens the drawer (and the current app's name when it has no tab). */
@Composable
private fun DrawerHandle(open: Boolean, currentExtra: HubSection?, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, tween(220), label = "arrow")
    Row(
        Modifier.fillMaxWidth().height(28.dp).clickable(onClick = onClick).testTag("hub_expand"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentExtra != null) {
            Icon(currentExtra.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                currentExtra.label(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(2.dp))
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = str(R.string.hub_all_apps),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).rotate(rotation),
        )
    }
}

/** Every app as a tile, four to a row. */
@Composable
private fun AppDrawer(selected: Int, onPick: (Int) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("hub_drawer"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            hubSections.withIndex().chunked(4).forEach { rowItems ->
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { (index, section) ->
                        val active = index == selected
                        Column(
                            Modifier.weight(1f).clickable { onPick(index) }.padding(vertical = 4.dp).testTag("hub_tile_$index"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(56.dp).background(
                                    if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(18.dp),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    section.icon, contentDescription = null,
                                    tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                section.label(), style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    // Keep the last row's tiles the same width as the full rows'.
                    repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
