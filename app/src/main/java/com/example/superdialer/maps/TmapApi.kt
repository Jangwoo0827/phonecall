package com.example.superdialer.maps

import com.example.superdialer.BuildConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LatLng(val lat: Double, val lng: Double)

data class Place(val name: String, val address: String, val lat: Double, val lng: Double) {
    val position: LatLng get() = LatLng(lat, lng)
}

enum class RouteMode { Transit, Car, Walk }

enum class LegMode { Walk, Subway, Bus, Rail, Car }

/** One stretch of a route: walking, one subway/bus/train ride, or the whole drive. */
class RouteLeg(
    val mode: LegMode,
    /** The line ("수도권2호선", "지선:1128") for rides, empty for walking and driving. */
    val title: String,
    val from: String,
    val to: String,
    val seconds: Int,
    val meters: Int,
    /** ARGB, or null for the default colour of the mode. */
    val color: Int?,
    val points: List<LatLng>,
) {
    /** "#RRGGBB" for the map line. */
    val cssColor: String
        get() = "#%06X".format((color ?: when (mode) {
            LegMode.Walk -> 0x7A7F87
            LegMode.Car -> 0x2A7CFF
            LegMode.Subway -> 0x2E7D32
            LegMode.Bus -> 0x1E88E5
            LegMode.Rail -> 0x6D4C41
        }) and 0xFFFFFF)
}

class RouteResult(
    val mode: RouteMode,
    val seconds: Int,
    val meters: Int,
    /** Fare in won: the transit fare, or the taxi fare for a drive; null if unknown. */
    val fare: Int?,
    val transfers: Int,
    val legs: List<RouteLeg>,
    /** Where the rider changes vehicle (for dots on the map). */
    val stops: List<LatLng>,
    /** Turn-by-turn lines for walking and driving. */
    val guide: List<String>,
)

/** A failure the screen can explain to the user. */
class TmapException(val kind: Kind, message: String? = null) : IOException(message) {
    enum class Kind { NoKey, QuotaExceeded, BadKey, Network, NoRoute, Other }
}

/**
 * T map (SK open API) calls and the parsing of their answers. The app key comes from BuildConfig (see app/build.gradle.kts);
 * the free plan blocks calls over its daily limit instead of charging, which shows up here as [TmapException.Kind.QuotaExceeded].
 * The parsing functions are pure and unit tested against real answers.
 */
object TmapApi {
    private const val BASE = "https://apis.openapi.sk.com"

    fun searchPlaces(query: String): List<Place> {
        val q = "version=1&count=10&resCoordType=WGS84GEO&reqCoordType=WGS84GEO&searchKeyword=" + URLEncoder.encode(query.trim(), "UTF-8")
        return parsePlaces(request("$BASE/tmap/pois?$q", null))
    }

    /** The address of a spot (for a place picked by tapping the map), or null if T map has none for it. */
    fun reverseGeocode(at: LatLng): Place? {
        val q = "version=1&coordType=WGS84GEO&addressType=A10&newAddressExtend=Y&lat=${at.lat}&lon=${at.lng}"
        return parseReverse(request("$BASE/tmap/geo/reversegeocoding?$q", null), at)
    }

    fun route(mode: RouteMode, from: LatLng, to: LatLng): RouteResult {
        val result = when (mode) {
            RouteMode.Transit -> {
                val body = JSONObject()
                    .put("startX", from.lng.toString()).put("startY", from.lat.toString())
                    .put("endX", to.lng.toString()).put("endY", to.lat.toString())
                    .put("count", 1).put("lang", 0).put("format", "json")
                parseTransit(request("$BASE/transit/routes", body.toString()))
            }
            RouteMode.Car, RouteMode.Walk -> {
                val body = JSONObject()
                    .put("startX", from.lng).put("startY", from.lat).put("endX", to.lng).put("endY", to.lat)
                    .put("reqCoordType", "WGS84GEO").put("resCoordType", "WGS84GEO")
                    .put("startName", "start").put("endName", "end")
                if (mode == RouteMode.Car) {
                    body.put("searchOption", 0)
                    parseRoad(request("$BASE/tmap/routes?version=1", body.toString()), RouteMode.Car)
                } else {
                    parseRoad(request("$BASE/tmap/routes/pedestrian?version=1", body.toString()), RouteMode.Walk)
                }
            }
        }
        return result ?: throw TmapException(TmapException.Kind.NoRoute)
    }

    private fun request(url: String, body: String?): String {
        val key = BuildConfig.TMAP_APP_KEY
        if (key.isBlank()) throw TmapException(TmapException.Kind.NoKey)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("appKey", key)
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) return text
            throw errorFor(code, text)
        } catch (e: TmapException) {
            throw e
        } catch (e: IOException) {
            throw TmapException(TmapException.Kind.Network, e.message)
        } finally {
            conn.disconnect()
        }
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    fun errorFor(code: Int, body: String): TmapException = when {
        code == 429 || body.contains("QUOTA", ignoreCase = true) -> TmapException(TmapException.Kind.QuotaExceeded, body.take(200))
        code == 401 || code == 403 -> TmapException(TmapException.Kind.BadKey, body.take(200))
        // Transit answers 400-ish with a "no route" style code when the two points are too close or unreachable.
        code == 400 || code == 404 || code == 500 -> TmapException(TmapException.Kind.NoRoute, body.take(200))
        else -> TmapException(TmapException.Kind.Other, "HTTP $code ${body.take(200)}")
    }

    /**
     * `fullAddress` is "district name, lot address, road address + building" in one comma-separated text; the road address
     * (the last part) is what people know. The building name, if any, becomes the place's name.
     */
    fun parseReverse(json: String, at: LatLng): Place? {
        return try {
            val info = JSONObject(json).optJSONObject("addressInfo") ?: return null
            val address = info.optString("fullAddress").split(',').map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: return null
            val building = info.optString("buildingName").trim()
            Place(building.ifEmpty { address }, address, at.lat, at.lng)
        } catch (e: JSONException) {
            null
        }
    }

    fun parsePlaces(json: String): List<Place> {
        return try {
            val pois = JSONObject(json).optJSONObject("searchPoiInfo")?.optJSONObject("pois")?.optJSONArray("poi") ?: return emptyList()
            (0 until pois.length()).mapNotNull { i ->
                val p = pois.optJSONObject(i) ?: return@mapNotNull null
                val lat = p.optString("noorLat").toDoubleOrNull() ?: p.optString("frontLat").toDoubleOrNull() ?: return@mapNotNull null
                val lng = p.optString("noorLon").toDoubleOrNull() ?: p.optString("frontLon").toDoubleOrNull() ?: return@mapNotNull null
                val name = p.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                Place(name, addressOf(p), lat, lng)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private fun addressOf(p: JSONObject): String {
        val road = p.optString("roadName").trim()
        val building = p.optString("firstBuildNo").trim()
        val head = listOf(p.optString("upperAddrName"), p.optString("middleAddrName")).filter { it.isNotBlank() }
        return if (road.isNotEmpty()) {
            (head + listOf(if (building.isNotEmpty() && building != "0") "$road $building" else road)).joinToString(" ")
        } else {
            (head + listOf(p.optString("lowerAddrName"), p.optString("firstNo").takeIf { it.isNotBlank() && it != "0" }.orEmpty()))
                .filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    /** "127.02,37.49 127.03,37.50" (lon,lat pairs) -> points. */
    fun parseLineString(text: String?): List<LatLng> {
        if (text.isNullOrBlank()) return emptyList()
        return text.trim().split(' ').mapNotNull { pair ->
            val parts = pair.split(',')
            val lng = parts.getOrNull(0)?.toDoubleOrNull()
            val lat = parts.getOrNull(1)?.toDoubleOrNull()
            if (lat != null && lng != null) LatLng(lat, lng) else null
        }
    }

    /** The GeoJSON answer of the car and walking route APIs. */
    fun parseRoad(json: String, mode: RouteMode): RouteResult? {
        return try {
            val features = JSONObject(json).optJSONArray("features") ?: return null
            var meters = 0
            var seconds = 0
            var fare: Int? = null
            val points = ArrayList<LatLng>()
            val guide = ArrayList<String>()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val props = f.optJSONObject("properties") ?: continue
                val geometry = f.optJSONObject("geometry") ?: continue
                if (i == 0) {
                    meters = props.optInt("totalDistance")
                    seconds = props.optInt("totalTime")
                    if (mode == RouteMode.Car) fare = props.optInt("taxiFare").takeIf { it > 0 }
                }
                when (geometry.optString("type")) {
                    "LineString" -> {
                        val coords = geometry.optJSONArray("coordinates") ?: continue
                        for (j in 0 until coords.length()) {
                            val c = coords.optJSONArray(j) ?: continue
                            points.add(LatLng(c.optDouble(1), c.optDouble(0)))
                        }
                    }
                    "Point" -> props.optString("description").trim().takeIf { it.isNotEmpty() }?.let { guide.add(it) }
                }
            }
            if (points.size < 2) return null
            val leg = RouteLeg(
                mode = if (mode == RouteMode.Car) LegMode.Car else LegMode.Walk,
                title = "", from = "", to = "", seconds = seconds, meters = meters, color = 0xFF2A7CFF.toInt(), points = points,
            )
            RouteResult(mode, seconds, meters, fare, 0, listOf(leg), emptyList(), guide)
        } catch (e: JSONException) {
            null
        }
    }

    /** The first itinerary of the transit route API. */
    fun parseTransit(json: String): RouteResult? {
        return try {
            val itinerary = JSONObject(json).optJSONObject("metaData")?.optJSONObject("plan")
                ?.optJSONArray("itineraries")?.optJSONObject(0) ?: return null
            val legsJson = itinerary.optJSONArray("legs") ?: return null
            val legs = ArrayList<RouteLeg>()
            val stops = ArrayList<LatLng>()
            for (i in 0 until legsJson.length()) {
                val l = legsJson.optJSONObject(i) ?: continue
                val mode = when (l.optString("mode")) {
                    "WALK" -> LegMode.Walk
                    "SUBWAY" -> LegMode.Subway
                    "BUS", "EXPRESSBUS" -> LegMode.Bus
                    else -> LegMode.Rail
                }
                var points = parseLineString(l.optJSONObject("passShape")?.optString("linestring"))
                if (points.size < 2) {
                    val steps = l.optJSONArray("steps")
                    if (steps != null) {
                        val all = ArrayList<LatLng>()
                        for (s in 0 until steps.length()) all.addAll(parseLineString(steps.optJSONObject(s)?.optString("linestring")))
                        points = all
                    }
                }
                val start = l.optJSONObject("start")
                val end = l.optJSONObject("end")
                if (points.size < 2 && start != null && end != null) {
                    points = listOf(LatLng(start.optDouble("lat"), start.optDouble("lon")), LatLng(end.optDouble("lat"), end.optDouble("lon")))
                }
                val color = l.optString("routeColor").takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }
                legs.add(
                    RouteLeg(
                        mode = mode,
                        title = if (mode == LegMode.Walk) "" else l.optString("route"),
                        from = start?.optString("name").orEmpty(),
                        to = end?.optString("name").orEmpty(),
                        seconds = l.optInt("sectionTime"),
                        meters = l.optInt("distance"),
                        color = color,
                        points = points,
                    ),
                )
                if (mode != LegMode.Walk && start != null) stops.add(LatLng(start.optDouble("lat"), start.optDouble("lon")))
                if (mode != LegMode.Walk && end != null) stops.add(LatLng(end.optDouble("lat"), end.optDouble("lon")))
            }
            if (legs.isEmpty()) return null
            RouteResult(
                mode = RouteMode.Transit,
                seconds = itinerary.optInt("totalTime"),
                meters = itinerary.optInt("totalDistance"),
                fare = itinerary.optJSONObject("fare")?.optJSONObject("regular")?.optInt("totalFare")?.takeIf { it > 0 },
                transfers = itinerary.optInt("transferCount"),
                legs = legs, stops = stops, guide = emptyList(),
            )
        } catch (e: JSONException) {
            null
        }
    }
}
