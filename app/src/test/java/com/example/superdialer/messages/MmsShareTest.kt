package com.example.superdialer.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsShareTest {
    @Test fun extensionsFollowTheMediaType() {
        assertEquals("jpg", MmsShare.extensionFor("image/jpeg"))
        assertEquals("png", MmsShare.extensionFor("IMAGE/PNG"))
        assertEquals("heic", MmsShare.extensionFor("image/heif"))
        assertEquals("mp4", MmsShare.extensionFor("video/mp4; codecs=avc1"))
        assertEquals("bin", MmsShare.extensionFor("application/octet-stream"))
        assertEquals("bin", MmsShare.extensionFor(""))
    }

    @Test fun stagedNamesNeverContainPartsOfTheOriginalName() {
        assertEquals("mms_1700000000000.jpg", MmsShare.stagedName("image/jpeg", 1_700_000_000_000))
        assertFalse(MmsShare.stagedName("image/png", 5).contains('/'))
    }

    @Test fun sizeLimitIsOnlyForAbsurdFiles() {
        assertFalse(MmsShare.isTooLarge(10L * 1024 * 1024))
        assertFalse(MmsShare.isTooLarge(MmsShare.MAX_BYTES))
        assertTrue(MmsShare.isTooLarge(MmsShare.MAX_BYTES + 1))
    }
}
