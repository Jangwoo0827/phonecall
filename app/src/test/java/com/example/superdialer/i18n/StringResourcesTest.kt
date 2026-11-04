package com.example.superdialer.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The English and the Korean texts must stay in step: same keys, same placeholders, and no Korean left in the English file. */
class StringResourcesTest {
    private fun load(dir: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        listOf("strings.xml", "strings_manual.xml").forEach { file ->
            val f = File("src/main/res/$dir/$file")
            if (f.exists()) {
                Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText())
                    .forEach { out[it.groupValues[1]] = it.groupValues[2] }
            }
        }
        return out
    }

    private val english = load("values")
    private val korean = load("values-ko")
    private val placeholder = Regex("""%(\d+)\$[sd]""")

    @Test fun bothLanguagesHaveTheSameKeys() {
        assertEquals("only in English: ${english.keys - korean.keys}", emptySet<String>(), english.keys - korean.keys)
        assertEquals("only in Korean: ${korean.keys - english.keys}", emptySet<String>(), korean.keys - english.keys)
        assertTrue(english.size > 500)
    }

    @Test fun placeholdersMatchBetweenTheLanguages() {
        val mismatches = english.keys.filter { key ->
            val en = placeholder.findAll(english.getValue(key)).map { it.groupValues[1] }.toSortedSet()
            val ko = placeholder.findAll(korean.getValue(key)).map { it.groupValues[1] }.toSortedSet()
            en != ko
        }
        assertEquals("different placeholders in: $mismatches", emptyList<String>(), mismatches)
    }

    @Test fun theEnglishFileHasNoKorean() {
        val hangul = Regex("[가-힣]")
        val offenders = english.filter { hangul.containsMatchIn(it.value) }.keys
        assertEquals("Korean text in the English file: $offenders", emptySet<String>(), offenders)
    }

    @Test fun koreanTextsAreActuallyKorean() {
        // a key that was not translated at all would repeat the English text in the Korean file
        val hangul = Regex("[가-힣]")
        val untranslated = korean.filter { (key, value) -> !hangul.containsMatchIn(value) && value == english[key] && value.any { it.isLetter() } }.keys
        // brand names and the like are fine to stay the same
        val allowed = setOf("app_name", "google", "kakaotalk", "checklist")
        assertTrue("Korean file repeats English in: ${untranslated - allowed}", (untranslated - allowed).size < 40)
    }
}
