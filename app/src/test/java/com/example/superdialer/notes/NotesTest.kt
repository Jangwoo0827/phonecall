package com.example.superdialer.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    private fun note(id: Long, text: String, at: Long, pinned: Boolean = false) = Note(id, text, at, pinned)

    @Test
    fun titleIsTheFirstNonBlankLineAndPreviewTheRest() {
        val text = "\n  Shopping list  \n\n milk \n eggs"
        assertEquals("Shopping list", Notes.titleOf(text))
        assertEquals("milk eggs", Notes.previewOf(text))
        assertEquals("", Notes.previewOf("only one line"))
        assertEquals("", Notes.titleOf("  \n "))
    }

    @Test
    fun searchIsCaseInsensitiveAndBlankMatchesAll() {
        assertTrue(Notes.matches("Buy MILK", "milk"))
        assertTrue(Notes.matches("anything", "  "))
        assertFalse(Notes.matches("Buy milk", "eggs"))
        assertTrue(Notes.matches("우유 사기", "우유"))
    }

    @Test
    fun pinnedNotesComeFirstThenNewest() {
        val sorted = Notes.sortForDisplay(listOf(note(1, "a", 10), note(2, "b", 30), note(3, "c", 5, pinned = true)))
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun upsertCreatesUpdatesAndRemoves() {
        val created = Notes.upsert(emptyList(), null, "  first  ", null, now = 100, max = 10)
        assertEquals(1L, created.id)
        assertEquals("first", created.list.single().text)

        val edited = Notes.upsert(created.list, 1L, "first edited", true, now = 200, max = 10)
        assertEquals(1, edited.list.size)
        assertEquals(200L, edited.list.single().updatedAt)
        assertTrue(edited.list.single().pinned)

        // pinned = null keeps the old state
        val kept = Notes.upsert(edited.list, 1L, "again", null, now = 300, max = 10)
        assertTrue(kept.list.single().pinned)

        val removed = Notes.upsert(kept.list, 1L, "   ", null, now = 400, max = 10)
        assertTrue(removed.list.isEmpty())
        assertNull(removed.id)

        // blank text for a note that does not exist creates nothing
        assertTrue(Notes.upsert(emptyList(), null, "", null, now = 1, max = 10).list.isEmpty())
    }

    @Test
    fun newIdsNeverReuseAnExistingOne() {
        val a = Notes.upsert(emptyList(), null, "a", null, 1, 10)
        val b = Notes.upsert(a.list, null, "b", null, 2, 10)
        val c = Notes.upsert(b.list.filter { it.id == 1L }, null, "c", null, 3, 10)
        assertEquals(setOf(1L, 2L), b.list.map { it.id }.toSet())
        assertEquals(2L, c.id)
    }

    @Test
    fun theLimitDropsTheOldestUnpinnedNoteButNeverTheOneJustSaved() {
        var list = emptyList<Note>()
        list = Notes.upsert(list, null, "pinned old", true, 1, 3).list
        list = Notes.upsert(list, null, "b", null, 2, 3).list
        list = Notes.upsert(list, null, "c", null, 3, 3).list
        list = Notes.upsert(list, null, "d", null, 4, 3).list
        assertEquals(3, list.size)
        assertEquals(setOf("pinned old", "c", "d"), list.map { it.text }.toSet())
    }

    @Test
    fun veryLongTextIsCut() {
        val result = Notes.upsert(emptyList(), null, "x".repeat(Notes.MAX_LENGTH + 500), null, 1, 10)
        assertEquals(Notes.MAX_LENGTH, result.list.single().text.length)
    }

    @Test
    fun encodeDecodeRoundTripAndBrokenDataIsIgnored() {
        val list = listOf(note(1, "한글\n두 줄", 5, pinned = true), note(2, "b", 6))
        assertEquals(list, Notes.decode(Notes.encode(list)))
        assertTrue(Notes.decode(null).isEmpty())
        assertTrue(Notes.decode("not json").isEmpty())
        // blank bodies and duplicate ids are dropped
        val messy = """[{"i":1,"t":1,"x":"a"},{"i":1,"t":2,"x":"dup"},{"i":2,"t":3,"x":"  "}]"""
        assertEquals(listOf("a"), Notes.decode(messy).map { it.text })
    }
}
