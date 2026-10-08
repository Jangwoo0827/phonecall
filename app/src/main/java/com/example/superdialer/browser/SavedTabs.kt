package com.example.superdialer.browser

/** One tab as it is written to disk: its address (null = start page) and the last known page title. */
data class SavedTab(val url: String?, val title: String)

/** Text form of the open tabs (kept in SharedPreferences) so they survive the app being closed. Pure, unit tested. */
object SavedTabs {
    private const val TAB_SEP = '\u0001'
    private const val FIELD_SEP = '\u0002'

    fun encode(tabs: List<SavedTab>): String =
        tabs.joinToString(TAB_SEP.toString()) { (it.url.orEmpty() + FIELD_SEP + it.title.replace(TAB_SEP, ' ').replace(FIELD_SEP, ' ')) }

    fun decode(text: String?): List<SavedTab> {
        if (text.isNullOrEmpty()) return emptyList()
        return text.split(TAB_SEP).map { entry ->
            val parts = entry.split(FIELD_SEP, limit = 2)
            SavedTab(parts[0].ifEmpty { null }, parts.getOrElse(1) { "" })
        }
    }
}
