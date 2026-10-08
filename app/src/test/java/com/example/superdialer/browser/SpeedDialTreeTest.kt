package com.example.superdialer.browser

import com.example.superdialer.account.SyncSnapshot.Link
import com.example.superdialer.browser.data.SpeedDial
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedDialTreeTest {
    private fun row(id: Long, title: String, pos: Int, parent: Long = 0, folder: Boolean = false) =
        SpeedDial(id = id, title = title, url = if (folder) "" else "https://$title.com", position = pos, parentId = parent, isFolder = folder)

    @Test fun flattenPutsTopLevelFirstThenFolderChildrenWithIndexedParents() {
        val rows = listOf(
            row(1, "b", 1), row(2, "work", 0, folder = true), row(3, "c", 0, parent = 2), row(4, "d", 1, parent = 2), row(5, "a", 2),
        )
        val flat = SpeedDialTree.flatten(rows)
        assertEquals(listOf("work", "b", "a", "c", "d"), flat.map { it.title })
        assertEquals(listOf(-1, -1, -1, 0, 0), flat.map { it.parent })
        assertEquals(listOf(true, false, false, false, false), flat.map { it.folder })
    }

    @Test fun planNumbersPositionsPerParent() {
        val links = listOf(Link("work", "", folder = true), Link("b", "https://b.com"), Link("c", "https://c.com", parent = 0), Link("d", "https://d.com", parent = 0))
        val planned = SpeedDialTree.plan(links)
        assertEquals(listOf(-1, -1, 0, 0), planned.map { it.parentIndex })
        assertEquals(listOf(0, 1, 0, 1), planned.map { it.position })
    }

    @Test fun planIgnoresBadParents() {
        val links = listOf(Link("a", "https://a.com", parent = 5), Link("b", "https://b.com", parent = 0))
        assertEquals(listOf(-1, -1), SpeedDialTree.plan(links).map { it.parentIndex })
    }

    @Test fun movedShiftsTheOthers() {
        assertEquals(listOf("b", "c", "a"), SpeedDialTree.moved(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), SpeedDialTree.moved(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), SpeedDialTree.moved(listOf("a", "b"), 0, 5))
    }
}
