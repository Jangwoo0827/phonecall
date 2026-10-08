package com.example.superdialer.games

import android.content.Context
import android.content.SharedPreferences

/** The board a game was left on (an opaque JSON text written by the game itself), per game id. This phone only. */
object GameStates {
    private const val PREFS = "game_state"
    private const val MAX_CHARS = 50_000

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun load(gameId: String): String = prefs?.getString(gameId, null).orEmpty()

    fun save(gameId: String, json: String) {
        if (json.length > MAX_CHARS) return
        prefs?.edit()?.putString(gameId, json)?.apply()
    }

    fun clear(gameId: String) {
        prefs?.edit()?.remove(gameId)?.apply()
    }
}
