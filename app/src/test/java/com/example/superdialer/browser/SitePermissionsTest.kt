package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionsTest {
    @Test fun originsAreReducedToSchemeHostAndPort() {
        assertEquals("https://example.com", SitePermissions.normalizeOrigin("https://Example.com:443/page?x=1#top"))
        assertEquals("https://example.com", SitePermissions.normalizeOrigin("https://example.com/"))
        assertEquals("http://example.com:8080", SitePermissions.normalizeOrigin("http://example.com:8080/a"))
        assertEquals("http://example.com", SitePermissions.normalizeOrigin("http://example.com:80"))
        assertEquals("http://[::1]:3000", SitePermissions.normalizeOrigin("http://[::1]:3000/x"))
        assertNull(SitePermissions.normalizeOrigin("file:///sdcard/a.html"))
        assertNull(SitePermissions.normalizeOrigin("javascript:alert(1)"))
        assertNull(SitePermissions.normalizeOrigin("not a url"))
    }

    @Test fun onlySecurePagesMayAsk() {
        assertTrue(SitePermissions.isSecureOrigin("https://example.com"))
        assertTrue(SitePermissions.isSecureOrigin("http://localhost:8080"))
        assertTrue(SitePermissions.isSecureOrigin("http://127.0.0.1:3000"))
        assertFalse(SitePermissions.isSecureOrigin("http://example.com"))
        assertFalse(SitePermissions.isSecureOrigin("http://192.168.0.5"))
        assertFalse(SitePermissions.isSecureOrigin("ftp://example.com"))
        assertFalse(SitePermissions.isSecureOrigin("garbage"))
    }

    @Test fun androidPermissionsStillMissingAreListedOnce() {
        val missing = SitePermissions.missingOsPermissions(listOf(SitePermission.Camera, SitePermission.Microphone)) { false }
        assertEquals(listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO"), missing)
        // approximate location is enough: nothing to ask for
        val coarseOnly = SitePermissions.missingOsPermissions(listOf(SitePermission.Location)) { it.endsWith("COARSE_LOCATION") }
        assertEquals(emptyList<String>(), coarseOnly)
        // nothing granted: both location permissions are requested together
        assertEquals(2, SitePermissions.missingOsPermissions(listOf(SitePermission.Location)) { false }.size)
    }

    @Test fun grantedByOsKeepsOnlyWhatAndroidAllows() {
        val granted = SitePermissions.grantedByOs(SitePermission.entries) { it == "android.permission.CAMERA" || it.endsWith("FINE_LOCATION") }
        assertEquals(listOf(SitePermission.Camera, SitePermission.Location), granted)
    }

    @Test fun answersAreMergedPerSite() {
        var all = SitePermissions.withAnswer(emptyMap(), "https://a.com", listOf(SitePermission.Camera), SiteDecision.Allow, 10)
        all = SitePermissions.withAnswer(all, "https://a.com", listOf(SitePermission.Microphone), SiteDecision.Deny, 10)
        assertEquals(
            mapOf(SitePermission.Camera to SiteDecision.Allow, SitePermission.Microphone to SiteDecision.Deny),
            all["https://a.com"],
        )
        all = SitePermissions.withAnswer(all, "https://a.com", listOf(SitePermission.Camera), SiteDecision.Deny, 10)
        assertEquals(SiteDecision.Deny, all["https://a.com"]?.get(SitePermission.Camera))
    }

    @Test fun theOldestSitesFallAwayPastTheLimit() {
        var all = emptyMap<String, Map<SitePermission, SiteDecision>>()
        listOf("https://a.com", "https://b.com", "https://c.com").forEach {
            all = SitePermissions.withAnswer(all, it, listOf(SitePermission.Location), SiteDecision.Allow, 2)
        }
        assertEquals(listOf("https://b.com", "https://c.com"), all.keys.toList())
        // touching b makes c the oldest
        all = SitePermissions.withAnswer(all, "https://b.com", listOf(SitePermission.Camera), SiteDecision.Allow, 2)
        all = SitePermissions.withAnswer(all, "https://d.com", listOf(SitePermission.Camera), SiteDecision.Allow, 2)
        assertEquals(listOf("https://b.com", "https://d.com"), all.keys.toList())
    }

    @Test fun roundTripAndBadInput() {
        val all = mapOf("https://a.com" to mapOf(SitePermission.Camera to SiteDecision.Allow, SitePermission.Location to SiteDecision.Deny))
        assertEquals(all, SitePermissions.decode(SitePermissions.encode(all)))
        assertEquals(emptyMap<String, Map<SitePermission, SiteDecision>>(), SitePermissions.decode("nope"))
        assertEquals(emptyMap<String, Map<SitePermission, SiteDecision>>(), SitePermissions.decode(null))
        // unknown permission names and answers are dropped
        val parsed = SitePermissions.decode("""{"https://a.com":{"camera":"allow","teleport":"allow","mic":"maybe"}}""")
        assertEquals(mapOf("https://a.com" to mapOf(SitePermission.Camera to SiteDecision.Allow)), parsed)
    }
}
