package com.example.superdialer.update

import com.example.superdialer.crash.CrashLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAndCrashTest {
    @Test fun comparesVersionsNumerically() {
        assertTrue(UpdateChecker.isNewer("0.4.1", "0.4.0"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("v1.0.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.4.0", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("0.3.9", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("0.4", "0.4.0"))
        assertFalse(UpdateChecker.isNewer("0.4.0-beta", "0.4.0"))
    }

    @Test fun parsesAReleaseWithItsApk() {
        val json = """{"tag_name":"v0.4.1","html_url":"https://github.com/x/y/releases/tag/v0.4.1","body":" notes ",
            "assets":[{"name":"checksums.txt","browser_download_url":"https://x/c"},{"name":"SuperDialer-v0.4.1.apk","browser_download_url":"https://x/a.apk"}]}"""
        val release = UpdateChecker.parseRelease(json)!!
        assertEquals("0.4.1", release.version)
        assertEquals("https://x/a.apk", release.apkUrl)
        assertEquals("notes", release.notes)
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v1","assets":[]}""")!!.apkUrl)
        assertNull(UpdateChecker.parseRelease("nope"))
    }

    @Test fun crashReportHasTheUsefulParts() {
        val text = CrashLog.format(0L, "0.4.0", "Samsung SM-S911N", 36, "main", IllegalStateException("boom"))
        assertTrue(text.contains("앱 버전: 0.4.0"))
        assertTrue(text.contains("Samsung SM-S911N"))
        assertTrue(text.contains("IllegalStateException: boom"))
    }
}
