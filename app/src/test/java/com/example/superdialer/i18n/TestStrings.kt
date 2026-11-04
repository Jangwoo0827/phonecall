package com.example.superdialer.i18n

import com.example.superdialer.R
import java.io.File
import java.util.Locale

/**
 * Unit tests have no Android resources, so the app's texts are read here straight from the string files
 * (res/values-ko, res/values) and fed to [Strings.resolver]. Install with [install] in tests whose code produces user texts.
 */
object TestStrings {
    private val names: Map<Int, String> by lazy {
        R.string::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }
    }

    private val korean: Map<String, String> by lazy { load("values-ko") }
    private val english: Map<String, String> by lazy { load("values") }

    private fun load(dir: String): Map<String, String> {
        val out = HashMap<String, String>()
        listOf("strings.xml", "strings_manual.xml").forEach { file ->
            val f = File("src/main/res/$dir/$file")
            if (f.exists()) {
                Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(f.readText()).forEach {
                    out[it.groupValues[1]] = unescape(it.groupValues[2])
                }
            }
        }
        return out
    }

    private fun unescape(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        val backslash = '\\'
        return s.replace("${backslash}n", "\n")
            .replace("$backslash'", "'")
            .replace("$backslash\"", "\"")
            .replace("$backslash@", "@")
            .replace("$backslash?", "?")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    /** Korean texts (what the app shows by default on a Korean phone). */
    fun install() = install(korean, Locale.KOREAN)

    fun installEnglish() = install(english, Locale.ENGLISH)

    private fun install(table: Map<String, String>, locale: Locale) {
        Strings.resolver = { id, args ->
            val name = names[id] ?: error("unknown string id $id")
            String.format(locale, table[name] ?: error("missing string $name"), *args)
        }
    }

    fun uninstall() {
        Strings.resolver = null
    }
}
