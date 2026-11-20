package com.example.superdialer.maps

import android.Manifest
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

/**
 * The Map section of the Apps tab. The map picture is T map's vector map (see [MapWeb]); everything on top of it is native:
 * place search, "my location", the chosen place with a transit / car / walk choice, and the route as a summary plus steps.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = MapModel.selected

    // A tap on the map picks that spot as the destination.
    val pinLabel = str(R.string.map_pick_point)
    DisposableEffect(pinLabel) {
        MapWeb.onMapClick = { lat, lng -> MapModel.selectPoint(lat, lng, pinLabel) }
        onDispose { MapWeb.onMapClick = null }
    }
    BackHandler(enabled = selected != null || MapModel.results.isNotEmpty()) { MapModel.clearSelection() }

    // Android's location permission, asked when the user first needs their position.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val next = afterPermission
        afterPermission = null
        if (result.values.any { it }) next?.invoke() else Toast.makeText(context, str(R.string.map_err_noloc), Toast.LENGTH_LONG).show()
    }

    /**
     * Finds the phone's position (asking for permission first), shows it on the map, and continues with it.
     * [quiet] is for the automatic look on opening: no message if there is no fix.
     */
    fun withLocation(centerOnMe: Boolean, quiet: Boolean = false, then: (LatLng) -> Unit) {
        val go = {
            scope.launch {
                val here = MyLocation.current(context)
                if (here == null) {
                    if (!quiet) {
                        val text = if (MyLocation.isSwitchedOn(context)) R.string.map_err_noloc else R.string.map_location_off
                        Toast.makeText(context, str(text), Toast.LENGTH_LONG).show()
                    }
                } else {
                    MapModel.me = here
                    MapWeb.showMe(here.lat, here.lng)
                    if (centerOnMe) MapWeb.center(here.lat, here.lng, 16)
                    then(here)
                }
            }
            Unit
        }
        if (MyLocation.hasPermission(context)) {
            go()
        } else {
            afterPermission = go
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Opening the map: ask for the location permission once (a map without "where am I" is half a map), and with it show
    // where the phone is. Denying is respected: the location button and the route buttons ask again when they are used.
    LaunchedEffect(Unit) {
        if (MyLocation.hasPermission(context)) {
            if (MapModel.me == null) withLocation(centerOnMe = MapModel.selected == null, quiet = true) {}
        } else if (!MapModel.askedLocationOnOpen) {
            MapModel.askedLocationOnOpen = true
            withLocation(centerOnMe = MapModel.selected == null, quiet = true) {}
        }
    }

    Column(modifier.fillMaxSize()) {
        // The map and what floats on it (search, results, "my location"); the card sits below it so it never hides the route.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MapPicture(Modifier.fillMaxSize())
            MapNotice()
            Column(Modifier.fillMaxSize()) {
                SearchBar()
                ResultsList()
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                    FloatingActionButton(
                        onClick = { withLocation(centerOnMe = true) {} },
                        modifier = Modifier.testTag("map_my_location"),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = str(R.string.map_my_location), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (selected != null) {
            PlaceCard(selected) { m -> withLocation(centerOnMe = false) { MapModel.findRoute(m, it) } }
        }
    }
}

/** The map itself: the app-level WebView, attached while this screen is shown. */
@Composable
private fun MapPicture(modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapWeb.attach(ctx)
            FrameLayout(ctx).apply {
                val web = MapWeb.webView()
                (web.parent as? ViewGroup)?.removeView(web)
                addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        onRelease = { frame ->
            frame.removeAllViews()
            MapWeb.detach()
        },
    )
}

@Composable
private fun MapNotice() {
    val error = MapWeb.error ?: return
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                str(if (error == "no-key") R.string.map_no_key else R.string.map_load_failed),
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SearchBar() {
    TextField(
        value = MapModel.query,
        onValueChange = MapModel::onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).testTag("map_search"),
        placeholder = { Text(str(R.string.map_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (MapModel.searching) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else if (MapModel.query.isNotEmpty()) {
                IconButton(onClick = { MapModel.clearSelection() }) {
                    Icon(Icons.Filled.Clear, contentDescription = str(R.string.t_clear_search))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun ResultsList() {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val results = MapModel.results
    val error = MapModel.searchError
    val showEmpty = MapModel.query.isNotBlank() && !MapModel.searching && results.isEmpty() && MapModel.selected == null
    if (results.isEmpty() && error == null && !showEmpty) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(max = 360.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        if (results.isEmpty()) {
            Text(
                str(if (error != null) errorText(error) else R.string.map_no_results),
                modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(results) { place ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            keyboard?.hide()
                            focus.clearFocus()
                            MapModel.select(place)
                        }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("map_result"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(place.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                place.address, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(place: Place, onMode: (RouteMode) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("map_card"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).heightIn(max = 300.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { MapModel.clearSelection() }) {
                    Icon(Icons.Filled.Clear, contentDescription = str(R.string.map_close))
                }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(RouteMode.Transit, R.string.map_mode_transit, Icons.Filled.DirectionsSubway, onMode)
                ModeChip(RouteMode.Car, R.string.map_mode_car, Icons.Filled.DirectionsCar, onMode)
                ModeChip(RouteMode.Walk, R.string.map_mode_walk, Icons.Filled.DirectionsWalk, onMode)
            }
            if (MapModel.mode == RouteMode.Transit) {
                Text(
                    str(R.string.map_transit_left, MapModel.transitUsedToday, MapModel.TRANSIT_DAILY_LIMIT),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            when {
                MapModel.routing -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(str(R.string.map_finding), modifier = Modifier.padding(start = 10.dp))
                }
                MapModel.routeError != null -> Text(
                    str(errorText(MapModel.routeError!!)), modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                MapModel.route != null -> RouteDetails(MapModel.route!!)
            }
        }
    }
}

@Composable
private fun ModeChip(mode: RouteMode, label: Int, icon: ImageVector, onMode: (RouteMode) -> Unit) {
    FilterChip(
        selected = MapModel.mode == mode && (MapModel.route != null || MapModel.routing),
        onClick = { onMode(mode) },
        label = { Text(str(label)) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag("map_mode_${mode.name.lowercase(Locale.US)}"),
    )
}

@Composable
private fun RouteDetails(route: RouteResult) {
    val context = LocalContext.current
    Column(Modifier.padding(top = 10.dp)) {
        val parts = buildList {
            add(formatDuration(route.seconds))
            add(formatDistance(route.meters))
            route.fare?.let {
                val money = NumberFormat.getIntegerInstance(Locale.KOREA).format(it)
                add(if (route.mode == RouteMode.Car) str(R.string.map_taxi, money) else str(R.string.map_fare_won, money))
            }
            if (route.mode == RouteMode.Transit && route.transfers > 0) add(str(R.string.map_transfers, route.transfers))
        }
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("map_summary"))
        LazyColumn(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (route.mode == RouteMode.Transit) {
                items(route.legs) { leg -> LegRow(leg) }
            } else {
                items(route.guide.take(40)) { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun LegRow(leg: RouteLeg) {
    val color = Color(0xFF000000.toInt() or (android.graphics.Color.parseColor(leg.cssColor) and 0xFFFFFF))
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(legIcon(leg.mode), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(legTitle(leg), fontWeight = FontWeight.SemiBold)
            val detail = if (leg.mode == LegMode.Walk) formatDistance(leg.meters) else "${leg.from} → ${leg.to}"
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(leg.seconds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(IntrinsicWidth))
    }
}

private val IntrinsicWidth = 64.dp

private fun legIcon(mode: LegMode): ImageVector = when (mode) {
    LegMode.Walk -> Icons.Filled.DirectionsWalk
    LegMode.Subway -> Icons.Filled.DirectionsSubway
    LegMode.Bus -> Icons.Filled.DirectionsBus
    LegMode.Rail -> Icons.Filled.Train
    LegMode.Car -> Icons.Filled.DirectionsCar
}

private fun legTitle(leg: RouteLeg): String = when (leg.mode) {
    LegMode.Walk -> str(R.string.map_leg_walk)
    LegMode.Subway -> str(R.string.map_leg_subway, leg.title.removePrefix("수도권"))
    LegMode.Bus -> str(R.string.map_leg_bus, leg.title.substringAfter(':'))
    LegMode.Rail -> str(R.string.map_leg_rail, leg.title)
    LegMode.Car -> str(R.string.map_leg_drive)
}

private fun errorText(kind: TmapException.Kind): Int = when (kind) {
    TmapException.Kind.NoKey -> R.string.map_no_key
    TmapException.Kind.QuotaExceeded -> R.string.map_err_quota
    TmapException.Kind.BadKey -> R.string.map_err_badkey
    TmapException.Kind.Network -> R.string.map_err_network
    TmapException.Kind.NoRoute -> R.string.map_err_noroute
    TmapException.Kind.Other -> R.string.map_err_other
}

internal fun formatDuration(seconds: Int): String {
    val minutes = max(1, (seconds + 30) / 60)
    return if (minutes >= 60) str(R.string.map_hours_minutes, minutes / 60, minutes % 60) else str(R.string.map_minutes, minutes)
}

internal fun formatDistance(meters: Int): String =
    if (meters >= 1000) "%.1fkm".format(Locale.US, meters / 1000.0) else "${meters}m"
