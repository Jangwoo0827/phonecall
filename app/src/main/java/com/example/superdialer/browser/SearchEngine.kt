package com.example.superdialer.browser

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import java.net.URLEncoder

/** The search engine the address bar uses for text that is not an address. [key] is what is stored and synced. */
enum class SearchEngine(val key: String, val label: String, private val searchBase: String) {
    GOOGLE("google", str(R.string.t_google), "https://www.google.com/search?q="),
    NAVER("naver", str(R.string.t_naver), "https://search.naver.com/search.naver?query="),
    BING("bing", str(R.string.t_microsoft), "https://www.bing.com/search?q=");

    fun searchUrl(query: String): String = searchBase + URLEncoder.encode(query, "UTF-8")

    /** True for an address that is a results page of this engine. */
    fun isSearchUrl(url: String): Boolean = url.startsWith(searchBase)

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: GOOGLE

        /** True for a results page of any of the engines. */
        fun isAnySearchUrl(url: String): Boolean = entries.any { it.isSearchUrl(url) }
    }
}
