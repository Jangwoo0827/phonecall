package com.example.superdialer.browser

import java.net.URLEncoder

/** Turns whatever the user typed in the address bar into a URL to load. */
object UrlResolver {
    const val HOME_URL = "https://www.google.com"
    private const val SEARCH_URL = "https://www.google.com/search?q="

    // host[:port][/path|?query|#fragment] — no scheme, no whitespace.
    private val hostLike = Regex("""^([^\s/:?#]+)(:\d{1,5})?([/?#]\S*)?$""")
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** Returns null for blank input. */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null

        // Explicit web URL.
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            return text
        }

        val match = hostLike.matchEntire(text)
        if (match != null) {
            val host = match.groupValues[1]
            val isLocal = host.equals("localhost", ignoreCase = true) || ipv4.matches(host)
            if (isLocal) return "http://$text"
            // "naver.com", "example.co.kr/path": a dot makes it a host. Single words are searches.
            if (host.contains('.') && !host.startsWith('.') && !host.endsWith('.')) return "https://$text"
        }

        return SEARCH_URL + URLEncoder.encode(text, "UTF-8")
    }

    /** Like [resolve], but null when the text is not an address (it would only turn into a search). */
    fun resolveAddress(input: String): String? {
        val url = resolve(input) ?: return null
        val typedUrl = input.trim().startsWith("http", ignoreCase = true)
        return if (url.startsWith(SEARCH_URL) && !typedUrl) null else url
    }

    /** Schemes the in-app browser loads itself. Everything else is handed off or blocked. */
    fun isWebScheme(scheme: String?): Boolean =
        scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
}
