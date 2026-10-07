package com.example.superdialer.browser

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.superdialer.games.GameSession

/**
 * Keeps the browser tabs and the open game alive for [KEEP_ALIVE_MS] after the app is left (back button,
 * swipe away while the process survives), so coming back within a few minutes finds everything as it was.
 *
 * Two layers: the [BrowserViewModel] (with its live WebViews) is owned here instead of by an Activity, and
 * a small snapshot (tab addresses, section, open game) is saved so that even if Android kills the process
 * in the meantime the pages are reopened (reloaded; scroll position and game progress are lost then).
 */
object BrowserSession {
    const val KEEP_ALIVE_MS = 3 * 60 * 1000L

    private const val PREFS = "browser_session"
    private const val KEY_TIME = "left_at"
    private const val KEY_TABS = "tabs"
    private const val KEY_SELECTED = "selected"
    private const val KEY_SECTION = "section"
    private const val KEY_GAME = "game"
    private const val SEPARATOR = "\u0001"

    private var viewModel: BrowserViewModel? = null
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { release() }

    /** The live instance, if any (null once expired). */
    fun peek(): BrowserViewModel? = viewModel

    fun get(app: Application): BrowserViewModel = viewModel ?: BrowserViewModel(app).also {
        viewModel = it
        restore(app, it)
    }

    /** The app left the screen: remember what is open and start the countdown. */
    fun onLeave(app: Application) {
        val vm = viewModel ?: return
        save(app, vm)
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, KEEP_ALIVE_MS)
    }

    /** The app is back: stop the countdown. */
    fun onReturn() {
        handler.removeCallbacks(expire)
    }

    private fun release() {
        handler.removeCallbacks(expire)
        viewModel?.release()
        viewModel = null
        GameSession.release()
    }

    private fun save(app: Application, vm: BrowserViewModel) {
        val urls = vm.tabs.map { if (it.isStart) "" else it.url }
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_TIME, System.currentTimeMillis())
            .putString(KEY_TABS, urls.joinToString(SEPARATOR))
            .putInt(KEY_SELECTED, vm.tabs.indexOfFirst { it.id == vm.selectedId })
            .putInt(KEY_SECTION, vm.hubSection)
            .putString(KEY_GAME, GameSession.playingId.orEmpty())
            .apply()
    }

    /** After a process restart: reopen what was open if the app was left less than [KEEP_ALIVE_MS] ago. */
    private fun restore(app: Application, vm: BrowserViewModel) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val leftAt = prefs.getLong(KEY_TIME, 0L)
        val fresh = leftAt > 0 && System.currentTimeMillis() - leftAt in 0..KEEP_ALIVE_MS
        val tabs = prefs.getString(KEY_TABS, null)
        val selected = prefs.getInt(KEY_SELECTED, -1)
        val section = prefs.getInt(KEY_SECTION, 0)
        val game = prefs.getString(KEY_GAME, null)?.ifEmpty { null }
        prefs.edit().clear().apply()
        if (!fresh || tabs == null) return

        tabs.split(SEPARATOR).forEach { vm.newTab(it.ifEmpty { null }) }
        vm.tabs.getOrNull(selected)?.let { vm.selectTab(it.id) }
        vm.hubSection = section
        if (game != null) GameSession.playingId = game
    }
}
