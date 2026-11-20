package com.example.superdialer.maps

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The state of the Map section (kept at app level, so switching apps and back leaves the search, the place and the route as
 * they were). The screen only shows it and forwards taps here; the network calls and the daily transit counter live here.
 */
object MapModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null
    private var routeJob: Job? = null
    private var prefs: android.content.SharedPreferences? = null

    var query by mutableStateOf("")
        private set
    var results by mutableStateOf<List<Place>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<TmapException.Kind?>(null)
        private set

    var selected by mutableStateOf<Place?>(null)
        private set
    var me by mutableStateOf<LatLng?>(null)

    var mode by mutableStateOf(RouteMode.Transit)
        private set
    var route by mutableStateOf<RouteResult?>(null)
        private set
    var routing by mutableStateOf(false)
        private set
    var routeError by mutableStateOf<TmapException.Kind?>(null)
        private set

    /** Transit answers used today on this phone (the free plan allows [TRANSIT_DAILY_LIMIT] a day). */
    var transitUsedToday by mutableStateOf(0)
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("map_usage", Context.MODE_PRIVATE)
        transitUsedToday = readUsage()
        MapWeb.onReady = ::redraw
    }

    /** The map page was (re)loaded: put my position, the pin and the route back on it. */
    private fun redraw() {
        me?.let { MapWeb.showMe(it.lat, it.lng) }
        val place = selected
        if (place != null) {
            MapWeb.showPin(place.lat, place.lng)
            route?.let { MapWeb.showRoute(it.legs, it.stops) } ?: MapWeb.center(place.lat, place.lng, 16)
        } else {
            me?.let { MapWeb.center(it.lat, it.lng, 15) }
        }
    }

    // --- Search ---------------------------------------------------------------------------------

    fun onQueryChange(text: String) {
        query = text
        searchJob?.cancel()
        searchError = null
        if (text.isBlank()) {
            results = emptyList()
            searching = false
            return
        }
        searchJob = scope.launch {
            delay(450) // wait for the typing to pause
            searching = true
            try {
                results = withContext(Dispatchers.IO) { TmapApi.searchPlaces(text) }
                searchError = null
            } catch (e: TmapException) {
                results = emptyList()
                searchError = e.kind
            } finally {
                searching = false
            }
        }
    }

    // --- Place and route ------------------------------------------------------------------------

    fun select(place: Place) {
        searchJob?.cancel()
        searching = false
        results = emptyList()
        query = place.name
        selected = place
        clearRoute()
        MapWeb.clearRoute()
        MapWeb.showPin(place.lat, place.lng)
        MapWeb.center(place.lat, place.lng, 16)
    }

    /** The user tapped the map: that spot becomes the destination (named by its coordinates until a search names it). */
    fun selectPoint(lat: Double, lng: Double, label: String) {
        select(Place(label, "%.5f, %.5f".format(Locale.US, lat, lng), lat, lng))
        // Name the spot by its address as soon as T map answers (if the user has not picked something else meanwhile).
        scope.launch {
            val named = try {
                withContext(Dispatchers.IO) { TmapApi.reverseGeocode(LatLng(lat, lng)) }
            } catch (e: TmapException) {
                null
            } ?: return@launch
            val current = selected
            if (current != null && current.lat == lat && current.lng == lng) {
                selected = named
                query = named.name
            }
        }
    }

    fun clearSelection() {
        routeJob?.cancel()
        selected = null
        query = ""
        results = emptyList()
        clearRoute()
        MapWeb.clearRoute()
        MapWeb.clearPin()
    }

    private fun clearRoute() {
        routeJob?.cancel()
        route = null
        routing = false
        routeError = null
    }

    /** Whether asking for [m] now would be refused here because today's free transit answers are used up. */
    fun transitBlocked(): Boolean = readUsage() >= TRANSIT_DAILY_LIMIT

    /** Finds the way from [from] to the selected place; answers from the last ten minutes are reused (they cost nothing). */
    fun findRoute(m: RouteMode, from: LatLng) {
        val to = selected?.position ?: return
        mode = m
        routeJob?.cancel()
        route = null
        routeError = null
        MapWeb.clearRoute()
        val key = cacheKey(m, from, to)
        cache[key]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let {
            showRoute(it.second)
            return
        }
        if (m == RouteMode.Transit && transitBlocked()) {
            routeError = TmapException.Kind.QuotaExceeded
            return
        }
        routing = true
        routeJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { TmapApi.route(m, from, to) }
                if (m == RouteMode.Transit) countTransit()
                cache[key] = System.currentTimeMillis() to result
                while (cache.size > 20) cache.remove(cache.keys.first())
                showRoute(result)
            } catch (e: TmapException) {
                if (e.kind == TmapException.Kind.QuotaExceeded && m == RouteMode.Transit) markTransitUsedUp()
                routeError = e.kind
            } finally {
                routing = false
            }
        }
    }

    private fun showRoute(result: RouteResult) {
        route = result
        routeError = null
        MapWeb.showRoute(result.legs, result.stops)
    }

    private val cache = LinkedHashMap<String, Pair<Long, RouteResult>>()

    private fun cacheKey(m: RouteMode, a: LatLng, b: LatLng) =
        "%s:%.4f,%.4f>%.4f,%.4f".format(Locale.US, m.name, a.lat, a.lng, b.lat, b.lng)

    // --- Daily transit counter (the free plan's 10 a day; Korean time, like the plan's reset) ----

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }.format(Date())

    private fun readUsage(): Int {
        val p = prefs ?: return 0
        return if (p.getString("day", "") == today()) p.getInt("transit", 0) else 0
    }

    private fun countTransit() {
        val n = readUsage() + 1
        prefs?.edit()?.putString("day", today())?.putInt("transit", n)?.apply()
        transitUsedToday = n
    }

    private fun markTransitUsedUp() {
        prefs?.edit()?.putString("day", today())?.putInt("transit", TRANSIT_DAILY_LIMIT)?.apply()
        transitUsedToday = TRANSIT_DAILY_LIMIT
    }

    const val TRANSIT_DAILY_LIMIT = 10
    private const val CACHE_MS = 10 * 60 * 1000L
}
