package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatBytesTest {
    @Test fun formatsSizes() {
        assertEquals("", formatBytes(-1))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("2.0 MB", formatBytes(2L * 1024 * 1024))
        assertEquals("1.25 GB", formatBytes(1280L * 1024 * 1024))
    }
}
