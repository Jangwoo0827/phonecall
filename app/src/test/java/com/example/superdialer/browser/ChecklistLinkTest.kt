package com.example.superdialer.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistLinkTest {
    @Test fun decodesJavascriptStrings() {
        assertEquals("{\"a\":1}", ChecklistLink.decodeJsString("\"{\\\"a\\\":1}\""))
        assertNull(ChecklistLink.decodeJsString("null"))
        assertNull(ChecklistLink.decodeJsString(null))
        assertNull(ChecklistLink.decodeJsString("123"))
    }

    @Test fun decidesWhenTheSessionMustBeWritten() {
        val now = 1_000_000_000L
        assertTrue(ChecklistLink.needsSession(null, now))
        assertTrue(ChecklistLink.needsSession("not json", now))
        assertTrue(ChecklistLink.needsSession("{}", now))
        // the user's own login on the page is never replaced
        assertFalse(ChecklistLink.needsSession("""{"access_token":"a","managed":false,"expires_at":1}""", now))
        // an app-managed one is renewed when it has less than 10 minutes left
        assertTrue(ChecklistLink.needsSession("""{"access_token":"a","managed":true,"expires_at":${now + 5 * 60_000}}""", now))
        assertFalse(ChecklistLink.needsSession("""{"access_token":"a","managed":true,"expires_at":${now + 30 * 60_000}}""", now))
    }

    @Test fun onlyTheChecklistAddressCounts() {
        assertTrue(ChecklistLink.isChecklist("https://jangwoo0827.github.io/checklist_summarizer/"))
        assertTrue(ChecklistLink.isChecklist("https://jangwoo0827.github.io/checklist_summarizer/index.html"))
        assertFalse(ChecklistLink.isChecklist("https://jangwoo0827.github.io/other/"))
        assertFalse(ChecklistLink.isChecklist("https://evil.example/https://jangwoo0827.github.io/checklist_summarizer/"))
        assertFalse(ChecklistLink.isChecklist(null))
    }
}
