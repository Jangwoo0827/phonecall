package com.example.superdialer.dialer

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class DialerViewModel : ViewModel() {

    /** Raw dial string: digits, '*', '#', and an optional leading '+'. */
    var number by mutableStateOf("")
        private set

    /** Incremented whenever an external intent asks to show the keypad. */
    var dialRequest by mutableIntStateOf(0)
        private set

    fun append(c: Char) {
        if (number.length >= MAX_LENGTH) return
        if (c.isDigit() || c == '*' || c == '#') number += c
    }

    /** Long-press on 0: a leading '+' for international numbers. */
    fun appendPlus() {
        if (number.isEmpty()) number = "+"
    }

    /** Replaces the typed number, e.g. when the user picks a suggestion. */
    fun replaceNumber(raw: String) {
        number = sanitize(raw)
    }

    fun backspace() {
        number = number.dropLast(1)
    }

    fun clear() {
        number = ""
    }

    /** Handles ACTION_DIAL / ACTION_VIEW with a tel: URI. Returns true if the intent was ours. */
    fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        if (intent.action != Intent.ACTION_DIAL && intent.action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return if (intent.action == Intent.ACTION_DIAL) {
            dialRequest++
            true
        } else {
            false
        }
        if (data.scheme != "tel") return false
        number = sanitize(data.schemeSpecificPart.orEmpty())
        dialRequest++
        return true
    }

    private fun sanitize(s: String): String {
        val sb = StringBuilder()
        for ((i, c) in s.withIndex()) {
            if (c.isDigit() || c == '*' || c == '#' || (c == '+' && i == 0)) sb.append(c)
        }
        return sb.take(MAX_LENGTH).toString()
    }

    private companion object {
        const val MAX_LENGTH = 30
    }
}
