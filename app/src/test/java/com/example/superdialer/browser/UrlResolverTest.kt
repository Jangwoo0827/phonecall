package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlResolverTest {
    private fun url(input: String) = UrlResolver.resolve(input)

    @Test fun blankIsNull() {
        assertNull(url(""))
        assertNull(url("   "))
    }

    @Test fun explicitWebUrlsAreKept() {
        assertEquals("https://example.com/a?b=1", url("https://example.com/a?b=1"))
        assertEquals("http://example.com", url(" http://example.com "))
    }

    @Test fun bareHostsGetHttps() {
        assertEquals("https://naver.com", url("naver.com"))
        assertEquals("https://www.example.co.kr/path?x=1", url("www.example.co.kr/path?x=1"))
        assertEquals("https://example.com:8443/x", url("example.com:8443/x"))
    }

    @Test fun localAddressesGetHttp() {
        assertEquals("http://localhost:8080", url("localhost:8080"))
        assertEquals("http://192.168.0.1/admin", url("192.168.0.1/admin"))
    }

    @Test fun everythingElseIsAGoogleSearch() {
        assertEquals("https://www.google.com/search?q=hello+world", url("hello world"))
        assertEquals("https://www.google.com/search?q=naver", url("naver"))
        assertEquals("https://www.google.com/search?q=%EC%98%A4%EB%8A%98+%EB%82%A0%EC%94%A8", url("오늘 날씨"))
    }

    @Test fun scriptAndOtherSchemesAreNeverLoadedAsUrls() {
        val js = url("javascript:alert(document.domain)")!!
        assertTrue(js.startsWith("https://www.google.com/search?q="))
        val file = url("file:///sdcard/secret.html")!!
        assertTrue(file.startsWith("https://www.google.com/search?q="))
    }

    @Test fun resolveAddressRejectsPlainSearchText() {
        assertEquals("https://naver.com", UrlResolver.resolveAddress("naver.com"))
        assertEquals("https://www.google.com/search?q=a", UrlResolver.resolveAddress("https://www.google.com/search?q=a"))
        assertNull(UrlResolver.resolveAddress("그냥 글자"))
        assertNull(UrlResolver.resolveAddress("   "))
    }

    @Test fun webSchemeCheck() {
        assertTrue(UrlResolver.isWebScheme("https"))
        assertTrue(UrlResolver.isWebScheme("HTTP"))
        assertFalse(UrlResolver.isWebScheme("file"))
        assertFalse(UrlResolver.isWebScheme(null))
    }
}
