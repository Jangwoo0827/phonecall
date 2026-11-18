package com.example.superdialer.music

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCodecTest {
    private val t = Track("dz:1", "Song", "Artist", 2, "Album", 3, "https://a/250x250-x.jpg", 200, "deezer://track/1", TrackSource.DEEZER, true)
    private val local = Track("lc:9", "File", "Me", 0, "", 4, "content://media/9", 99, "content://media/9", TrackSource.LOCAL)

    @Test fun tracksSurviveASaveAndLoad() {
        val back = MusicCodec.tracks(JSONArray(MusicCodec.tracks(listOf(t, local)).toString()))
        assertEquals(listOf(t, local), back)
        assertTrue(back[1].isLocal)
    }

    @Test fun playlistsSurviveASaveAndLoad() {
        val p = UserPlaylist("id-1", "Road trip", listOf(t, local), 123L)
        val json = MusicCodec.userPlaylist(p).toString()
        assertEquals(p, MusicCodec.userPlaylist(org.json.JSONObject(json)))
    }

    @Test fun libraryItemsSurviveASaveAndLoad() {
        val album = Album(1, "A", "B", 2, null, "1999", 12)
        val artist = Artist(3, "Name", "https://p", 77)
        val info = PlaylistInfo(4, "P", "Owner", null, 30, "desc")
        assertEquals(album, MusicCodec.album(MusicCodec.album(album)))
        assertEquals(artist, MusicCodec.artist(MusicCodec.artist(artist)))
        assertEquals(info, MusicCodec.playlistInfo(MusicCodec.playlistInfo(info)))
    }

    @Test fun brokenSavedDataIsIgnored() {
        assertNull(MusicCodec.track(org.json.JSONObject("""{"title":"no id"}""")))
        assertTrue(MusicCodec.tracks(null as JSONArray?).isEmpty())
        val mixed = JSONArray("""[{"id":"dz:5","title":"ok"},{"title":"bad"},7]""")
        assertEquals(listOf("dz:5"), MusicCodec.tracks(mixed).map { it.id })
    }
}
