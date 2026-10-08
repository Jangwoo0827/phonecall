package com.example.superdialer.browser

import com.example.superdialer.account.SyncSnapshot
import com.example.superdialer.browser.data.SpeedDial

/** Pure helpers for the start page's tiles and folders (one level of folders). */
object SpeedDialTree {
    /** A row to insert when rebuilding the tiles from a synced list; [parentIndex] points into the same list, -1 = top level. */
    data class Planned(val link: SyncSnapshot.Link, val parentIndex: Int, val position: Int)

    /** Rows in display order: top level first (by position), then each folder's children; folders are referenced by list index. */
    fun flatten(rows: List<SpeedDial>): List<SyncSnapshot.Link> {
        val roots = rows.filter { it.parentId == 0L }.sortedWith(compareBy({ it.position }, { it.id }))
        val folderIndex = HashMap<Long, Int>()
        val out = ArrayList<SyncSnapshot.Link>()
        roots.forEach { root ->
            if (root.isFolder) folderIndex[root.id] = out.size
            out += SyncSnapshot.Link(root.title, root.url, folder = root.isFolder, parent = -1)
        }
        roots.filter { it.isFolder }.forEach { folder ->
            rows.filter { it.parentId == folder.id && !it.isFolder }
                .sortedWith(compareBy({ it.position }, { it.id }))
                .forEach { child ->
                    out += SyncSnapshot.Link(child.title, child.url, folder = false, parent = folderIndex.getValue(folder.id))
                }
        }
        return out
    }

    /** Positions for inserting [links]; a link pointing at a missing or non-folder parent falls back to the top level. */
    fun plan(links: List<SyncSnapshot.Link>): List<Planned> {
        val counters = HashMap<Int, Int>()
        return links.map { link ->
            val parent = link.parent.takeIf { it in links.indices && links[it].folder && !link.folder } ?: -1
            val position = counters.merge(parent, 1, Int::plus)!! - 1
            Planned(link, parent, position)
        }
    }

    /** [list] with the element at [from] moved to index [to] (others shift). */
    fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val copy = list.toMutableList()
        copy.add(to, copy.removeAt(from))
        return copy
    }
}
