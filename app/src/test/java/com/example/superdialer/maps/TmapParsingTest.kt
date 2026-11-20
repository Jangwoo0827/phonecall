package com.example.superdialer.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parsers run against real T map answers saved in src/test/resources/tmap (no key inside). */
class TmapParsingTest {
    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("tmap/$name")!!.readText(Charsets.UTF_8)

    @Test
    fun placesCarryNameAddressAndPosition() {
        val places = TmapApi.parsePlaces(fixture("poi.json"))
        assertTrue(places.isNotEmpty())
        val first = places.first()
        assertTrue(first.name.contains("강남역"))
        assertTrue(first.address.contains("강남"))
        assertEquals(37.498, first.lat, 0.01)
        assertEquals(127.028, first.lng, 0.01)
    }

    @Test
    fun brokenPlaceAnswersGiveAnEmptyList() {
        assertTrue(TmapApi.parsePlaces("").isEmpty())
        assertTrue(TmapApi.parsePlaces("{}").isEmpty())
        assertTrue(TmapApi.parsePlaces("""{"searchPoiInfo":{"pois":{"poi":[{"name":"","noorLat":"1","noorLon":"2"},{"name":"x","noorLat":"a","noorLon":"b"}]}}}""").isEmpty())
    }

    @Test
    fun walkingRouteHasOneLineAndTurnByTurnText() {
        val r = TmapApi.parseRoad(fixture("walk.json"), RouteMode.Walk)
        assertNotNull(r)
        r!!
        assertEquals(RouteMode.Walk, r.mode)
        assertTrue(r.meters > 0 && r.seconds > 0)
        assertEquals(1, r.legs.size)
        assertEquals(LegMode.Walk, r.legs[0].mode)
        assertTrue(r.legs[0].points.size >= 2)
        assertTrue(r.guide.isNotEmpty())
        assertNull(r.fare)
    }

    @Test
    fun drivingRouteCarriesTheTaxiFare() {
        val r = TmapApi.parseRoad(fixture("car.json"), RouteMode.Car)!!
        assertEquals(LegMode.Car, r.legs[0].mode)
        assertTrue((r.fare ?: 0) > 0)
        assertTrue(r.legs[0].points.size >= 2)
    }

    @Test
    fun transitRouteHasWalkAndSubwayLegsWithColoursAndShapes() {
        val r = TmapApi.parseTransit(fixture("transit.json"))!!
        assertEquals(RouteMode.Transit, r.mode)
        assertTrue(r.seconds > 0 && r.meters > 0)
        assertNotNull(r.fare)
        assertTrue(r.legs.any { it.mode == LegMode.Walk })
        val subway = r.legs.filter { it.mode == LegMode.Subway }
        assertTrue(subway.isNotEmpty())
        subway.forEach {
            assertTrue(it.title.contains("호선"))
            assertNotNull(it.color)
            assertTrue(it.points.size >= 2)
            assertTrue(it.from.isNotEmpty() && it.to.isNotEmpty())
        }
        r.legs.forEach { assertTrue(it.points.size >= 2) }
        assertTrue(r.stops.isNotEmpty())
        assertTrue(r.legs[0].cssColor.matches(Regex("#[0-9A-F]{6}")))
    }

    @Test
    fun lineStringsReadLonLatPairs() {
        val pts = TmapApi.parseLineString("127.5,37.5 127.6,37.6 bad 1")
        assertEquals(2, pts.size)
        assertEquals(37.5, pts[0].lat, 0.0)
        assertEquals(127.5, pts[0].lng, 0.0)
        assertTrue(TmapApi.parseLineString(null).isEmpty())
    }

    @Test
    fun errorsAreExplainedByKind() {
        assertEquals(TmapException.Kind.QuotaExceeded, TmapApi.errorFor(429, "").kind)
        assertEquals(TmapException.Kind.QuotaExceeded, TmapApi.errorFor(200, "QUOTA_EXCEEDED").kind)
        assertEquals(TmapException.Kind.BadKey, TmapApi.errorFor(403, "").kind)
        assertEquals(TmapException.Kind.NoRoute, TmapApi.errorFor(400, "").kind)
        assertEquals(TmapException.Kind.Other, TmapApi.errorFor(502, "").kind)
    }

    @Test
    fun reverseGeocodeGivesTheRoadAddressAndBuildingName() {
        val place = TmapApi.parseReverse(fixture("reverse.json"), LatLng(37.54412, 127.04633))!!
        assertEquals("서울숲 M타워", place.name)
        assertTrue(place.address.contains("뚝섬로1길"))
        assertEquals(37.54412, place.lat, 0.0)
        assertNull(TmapApi.parseReverse("{}", LatLng(0.0, 0.0)))
    }
}
