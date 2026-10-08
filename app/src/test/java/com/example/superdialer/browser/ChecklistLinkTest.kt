package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistLinkTest {
    @Test fun decodesQuotedIdsAndRejectsEverythingElse() {
        assertEquals("jangwoo", ChecklistLink.decodeJsString("\"jangwoo\""))
        assertEquals("장우_1", ChecklistLink.decodeJsString("\"장우_1\""))
        assertNull(ChecklistLink.decodeJsString("null"))
        assertNull(ChecklistLink.decodeJsString(null))
        assertNull(ChecklistLink.decodeJsString("\"x\""))
        assertNull(ChecklistLink.decodeJsString("\"has space\""))
        assertNull(ChecklistLink.decodeJsString("\"bad\\\");alert(1)\""))
        assertNull(ChecklistLink.decodeJsString("123"))
    }

    @Test fun onlyTheChecklistAddressCounts() {
        assertTrue(ChecklistLink.isChecklist("https://jangwoo0827.github.io/checklist_summarizer/"))
        assertTrue(ChecklistLink.isChecklist("https://jangwoo0827.github.io/checklist_summarizer/index.html"))
        assertFalse(ChecklistLink.isChecklist("https://jangwoo0827.github.io/other/"))
        assertFalse(ChecklistLink.isChecklist("https://evil.example/https://jangwoo0827.github.io/checklist_summarizer/"))
        assertFalse(ChecklistLink.isChecklist(null))
    }
}
