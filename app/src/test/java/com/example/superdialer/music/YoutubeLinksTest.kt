package com.example.superdialer.music

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YoutubeLinksTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun findsTheVideoIdInEveryKindOfLink() {
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=42",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://youtu.be/$id?si=abc",
            "youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "  https://youtu.be/$id  ",
        ).forEach { assertEquals(it, id, YoutubeLinks.videoId(it)) }
    }

    @Test fun refusesWhatIsNotAVideoLink() {
        listOf(
            "https://www.youtube.com/results?search_query=queen",
            "https://www.youtube.com/@channel",
            "https://example.com/watch?v=$id",
            "https://www.youtube.com/watch?v=short",
            "not a link at all",
            "",
        ).forEach { assertNull(it, YoutubeLinks.videoId(it)) }
        assertFalse(YoutubeLinks.isVideoLink("https://youtu.be/"))
        assertTrue(YoutubeLinks.isVideoLink("https://youtu.be/$id"))
    }

    @Test fun buildsASongFromTheOEmbedAnswer() {
        val t = YoutubeLinks.fromOEmbed(id, JSONObject("""{"title":"Never Gonna Give You Up","author_name":"Rick Astley"}"""))
        assertEquals("yt:$id", t.id)
        assertEquals("Never Gonna Give You Up", t.title)
        assertEquals("Rick Astley", t.artist)
        assertEquals("https://www.youtube.com/watch?v=$id", t.uri)
        assertEquals("https://i.ytimg.com/vi/$id/hqdefault.jpg", t.artUrl)
        assertTrue(t.isLink)
        assertFalse(t.isPreview)
    }

    @Test fun buildsTheSearchAddress() {
        assertEquals("https://m.youtube.com/results?search_query=die+for+you+valorant", YoutubeLinks.searchUrl(" die for you valorant "))
    }
}
