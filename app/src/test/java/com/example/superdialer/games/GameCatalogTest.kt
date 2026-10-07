package com.example.superdialer.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameCatalogTest {
    @Test fun parsesEntries() {
        val games = GameCatalog.parse(
            """[{"id":"a","title":"A","description":"d","path":"a/index.html","color":"#112233"}]"""
        )
        assertEquals(listOf(GameInfo("a", "A", "d", "a/index.html", "#112233")), games)
    }

    @Test fun missingOptionalFieldsGetDefaults() {
        val game = GameCatalog.parse("""[{"id":"a","title":"A","path":"a/index.html"}]""").single()
        assertEquals("", game.description)
        assertEquals("#607D8B", game.color)
    }

    @Test fun skipsBrokenAndUnsafeEntriesButKeepsTheRest() {
        val games = GameCatalog.parse(
            """[
              {"id":"","title":"no id","path":"x/index.html"},
              {"id":"t","title":"traversal","path":"../secret.html"},
              {"id":"abs","title":"absolute","path":"/etc/passwd"},
              {"id":"ok","title":"OK","path":"ok/index.html"},
              {"id":"ok","title":"duplicate id","path":"other/index.html"},
              42
            ]"""
        )
        assertEquals(listOf("ok"), games.map { it.id })
        assertEquals("OK", games.single().title)
    }

    @Test fun invalidJsonIsEmpty() {
        assertTrue(GameCatalog.parse("not json").isEmpty())
        assertTrue(GameCatalog.parse("{}").isEmpty())
    }
}
