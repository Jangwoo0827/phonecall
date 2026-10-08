package com.example.superdialer.games

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/** Best score per game id, kept locally. Observable from Compose. Call [init] once from the Application. */
object GameScores {
    private const val PREFS = "game_scores"
    private var prefs: SharedPreferences? = null
    private val best = mutableStateMapOf<String, Int>()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.all.forEach { (id, value) -> if (value is Int) best[id] = value }
    }

    fun best(gameId: String): Int = best[gameId] ?: 0

    /** A copy of every stored best score (for account sync). */
    fun all(): Map<String, Int> = best.toMap()

    /** Stores [score] if it beats the current best. Main thread only. */
    fun submit(gameId: String, score: Int): Boolean {
        if (score <= best(gameId)) return false
        best[gameId] = score
        prefs?.edit()?.putInt(gameId, score)?.apply()
        return true
    }
}
