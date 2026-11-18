package com.example.superdialer.music

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiusJsonTest {
    private val one = """
        {"id":"Ma0blKV","title":"Light the way","duration":227,"is_streamable":true,"access":{"stream":true,"download":true},
         "permalink":"/Stony100/light-the-way","artwork":{"150x150":"https://h/150.jpg","480x480":"https://h/480.jpg"},
         "user":{"name":"Stony100","id":"17xp6XY"}}
    """.trimIndent()

    @Test fun parsesAFullLengthTrack() {
        val t = AudiusJson.track(JSONObject(one))!!
        assertEquals("au:Ma0blKV", t.id)
        assertEquals("Stony100", t.artist)
        assertEquals(227, t.durationSec)
        assertEquals("https://h/480.jpg", t.artUrl)
        assertEquals("https://api.audius.co/v1/tracks/Ma0blKV/stream?app_name=SuperDialer", t.uri)
        assertEquals("https://audius.co/Stony100/light-the-way", t.link)
        assertFalse(t.isPreview)
        assertFalse(t.isLocal)
    }

    @Test fun gatedOrEmptyTracksAreSkipped() {
        assertNull(AudiusJson.track(JSONObject("""{"id":"a","title":"x","duration":10,"access":{"stream":false}}""")))
        assertNull(AudiusJson.track(JSONObject("""{"id":"a","title":"x","duration":10,"is_streamable":false}""")))
        assertNull(AudiusJson.track(JSONObject("""{"id":"a","title":"x","duration":0}""")))
        assertNull(AudiusJson.track(JSONObject("""{"title":"no id","duration":10}""")))
    }

    private fun au(title: String, artist: String) = Track("au:$title", title, artist, 0, "", 0, null, 200, "u", TrackSource.AUDIUS)
    private val wanted = Track("dz:1", "Die For You", "VALORANT", 5, "Alb", 6, "https://art", 29, "deezer://track/1", TrackSource.DEEZER, true)

    @Test fun matchesTheSameSongIgnoringBracketsAndPunctuation() {
        val found = listOf(au("Something else", "x"), au("Die for You (feat. Grabbitz) VALORANT", "fan"), au("Die For You - Remix", "y"))
        // the artist is not in the uploader's name but is in the title, which is good enough
        assertEquals("Die for You (feat. Grabbitz) VALORANT", AudiusJson.bestMatch(wanted, found)!!.title)
    }

    @Test fun aSameTitleByAnUnrelatedArtistIsNotTheSong() {
        assertNull(AudiusJson.bestMatch(wanted, listOf(au("Die For You", "somebody else"))))
    }

    @Test fun prefersTheMatchingArtistAndRefusesAWrongSong() {
        val found = listOf(au("Die For You", "somebody"), au("Die For You", "Valorant Music"))
        assertEquals("Valorant Music", AudiusJson.bestMatch(wanted, found)!!.artist)
        assertNull(AudiusJson.bestMatch(wanted, listOf(au("Die Another Day", "x"))))
        assertNull(AudiusJson.bestMatch(wanted, emptyList()))
    }

    @Test fun theFullVersionKeepsTheOriginalDetails() {
        val full = au("die-for-you-official-video-valorant", "ccagri").copy(artUrl = "https://rip", uri = "https://stream")
        val shown = AudiusJson.asFullVersionOf(wanted, full)
        assertEquals("Die For You", shown.title)
        assertEquals("VALORANT", shown.artist)
        assertEquals("https://art", shown.artUrl)
        assertEquals("https://stream", shown.uri)
        assertEquals(TrackSource.AUDIUS, shown.source)
        assertTrue(shown.explicit)
    }
}
