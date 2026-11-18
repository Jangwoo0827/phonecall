package com.example.superdialer.music

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeezerJsonTest {
    private val trackJson = """
        {"id":3135556,"title":"Harder, Better","title_short":"Harder","preview":"https://cdn/p.mp3","duration":224,"explicit_lyrics":true,
         "artist":{"id":27,"name":"Daft Punk"},
         "album":{"id":302127,"title":"Discovery","cover_medium":"https://img/250x250-000000-80-0-0.jpg"}}
    """.trimIndent()

    @Test fun parsesATrack() {
        val t = DeezerJson.track(JSONObject(trackJson))!!
        assertEquals("dz:3135556", t.id)
        assertEquals("Harder, Better", t.title)
        assertEquals("Daft Punk", t.artist)
        assertEquals(27L, t.artistId)
        assertEquals("Discovery", t.album)
        assertEquals(224, t.durationSec)
        assertEquals("deezer://track/3135556", t.uri)
        assertEquals(3135556L, t.deezerId)
        assertTrue(t.explicit)
        assertFalse(t.isLocal)
    }

    @Test fun tracksWithoutAPreviewAreDropped() {
        val none = JSONObject("""{"id":1,"title":"x","preview":"","artist":{"id":2,"name":"a"}}""")
        assertNull(DeezerJson.track(none))
        // a track that does not mention a preview at all (album tracklists do) is kept
        assertTrue(DeezerJson.track(JSONObject("""{"id":1,"title":"x","artist":{"id":2,"name":"a"}}""")) != null)
    }

    @Test fun albumTracklistsInheritTheAlbum() {
        val root = JSONObject(
            """{"id":9,"title":"Alb","release_date":"2001-03-12","cover_medium":"https://img/250x250-a.jpg","artist":{"id":5,"name":"Art"},
                "tracks":{"data":[{"id":11,"title":"One","preview":"u","duration":10},{"id":12,"title":"Two","preview":"u","duration":20}]}}""",
        )
        val d = DeezerJson.albumDetail(root)!!
        assertEquals("2001", d.album.year)
        assertEquals(2, d.tracks.size)
        assertEquals("Art", d.tracks[0].artist)
        assertEquals("Alb", d.tracks[1].album)
        assertEquals(9L, d.tracks[1].albumId)
        assertEquals("https://img/250x250-a.jpg", d.tracks[0].artUrl)
    }

    @Test fun brokenEntriesAreSkipped() {
        val root = JSONObject("""{"data":[{"id":0},{"id":7,"name":"Ok"},{"nope":1}]}""")
        assertEquals(listOf("Ok"), DeezerJson.artists(root.optJSONArray("data")).map { it.name })
    }

    @Test fun readsAnErrorAnswer() {
        assertEquals("Quota limit exceeded", DeezerJson.errorMessage(JSONObject("""{"error":{"type":"Exception","message":"Quota limit exceeded","code":4}}""")))
        assertNull(DeezerJson.errorMessage(JSONObject("""{"data":[]}""")))
    }

    @Test fun parsesAChart() {
        val root = JSONObject(
            """{"tracks":{"data":[$trackJson]},"albums":{"data":[{"id":1,"title":"A","artist":{"id":2,"name":"B"}}]},
                "artists":{"data":[{"id":3,"name":"C","nb_fan":5}]},"playlists":{"data":[{"id":4,"title":"P","user":{"name":"U"},"nb_tracks":8}]}}""",
        )
        val c = DeezerJson.chart(root)
        assertEquals(1, c.tracks.size)
        assertEquals("A", c.albums[0].title)
        assertEquals(5, c.artists[0].fans)
        assertEquals("U", c.playlists[0].owner)
        assertEquals(8, c.playlists[0].trackCount)
    }

    @Test fun formatsDurationsAndCounts() {
        assertEquals("3:07", formatDuration(187))
        assertEquals("0:05", formatDuration(5))
        assertEquals("1:02:09", formatDuration(3729))
        assertEquals("0:00", formatDuration(-4))
        assertEquals("950", compactCount(950))
        assertEquals("1.2K", compactCount(1234))
        assertEquals("3M", compactCount(3_000_000))
        assertEquals("12.9M", compactCount(12_900_000))
    }

    @Test fun picksAnotherArtworkSize() {
        assertEquals("https://x/500x500-000000-80-0-0.jpg", artAtSize("https://x/250x250-000000-80-0-0.jpg", 500))
        assertNull(artAtSize(null, 500))
        assertEquals("content://media/1", artAtSize("content://media/1", 500))
    }
}
