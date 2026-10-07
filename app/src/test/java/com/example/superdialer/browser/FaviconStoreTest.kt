package com.example.superdialer.browser

import com.example.superdialer.browser.data.FaviconStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconStoreTest {
    private val base = "https://example.com/"
    private fun pick(html: String) = FaviconStore.pickIconUrl(html, base)

    @Test fun hostIsLowercasedAndNullForNonUrls() {
        assertEquals("m.naver.com", FaviconStore.hostOf("https://M.Naver.com/path?q=1"))
        assertNull(FaviconStore.hostOf("about:blank"))
        assertNull(FaviconStore.hostOf("not a url"))
    }

    @Test fun picksDeclaredIconAndResolvesRelativePaths() {
        assertEquals("https://example.com/static/fav.png", pick("""<head><link rel="icon" href="/static/fav.png"></head>"""))
        assertEquals("https://example.com/a/b.png", pick("""<link rel='shortcut icon' href='b.png'>""".replace("b.png", "/a/b.png")))
    }

    @Test fun prefersAppleTouchIconThenLargestSize() {
        val html = """
            <link rel="icon" sizes="16x16" href="/16.png">
            <link rel="icon" sizes="192x192" href="/192.png">
            <link rel="apple-touch-icon" href="/apple.png">
        """
        assertEquals("https://example.com/apple.png", pick(html))
        assertEquals("https://example.com/192.png", pick("""<link rel="icon" sizes="16x16" href="/16.png"><link rel="icon" sizes="192x192" href="/192.png">"""))
    }

    @Test fun attributeOrderDoesNotMatter() {
        assertEquals("https://example.com/x.png", pick("""<LINK HREF="/x.png" TYPE="image/png" REL="ICON">"""))
    }

    @Test fun skipsSvgDataAndNonIconLinks() {
        assertNull(pick("""<link rel="icon" href="/logo.svg">"""))
        assertNull(pick("""<link rel="icon" href="data:image/png;base64,AAAA">"""))
        assertNull(pick("""<link rel="stylesheet" href="/site.css"><link rel="mask-icon" href="/m.svg">"""))
        assertNull(pick("<html>no links</html>"))
    }
}
