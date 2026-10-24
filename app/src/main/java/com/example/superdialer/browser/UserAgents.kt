package com.example.superdialer.browser

/** The "User-Agent" a tab introduces itself with. Sites send their desktop page to a desktop one ("PC 사이트 보기"). */
object UserAgents {
    private const val FALLBACK_CHROME = "120.0.0.0"
    private val CHROME_VERSION = Regex("Chrome/([0-9.]+)")

    /**
     * A desktop Chrome on Linux with the same Chrome version as this phone's WebView, built from the phone's own
     * (mobile) user agent: sites tell phones apart by "Android" and "Mobile", so both are left out.
     */
    fun desktop(mobileUserAgent: String): String {
        val version = CHROME_VERSION.find(mobileUserAgent)?.groupValues?.get(1) ?: FALLBACK_CHROME
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }
}
