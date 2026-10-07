package com.example.superdialer.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small persisted settings holder. Call [init] once from the Application. */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_DTMF = "dtmf_enabled"
    private const val KEY_ONBOARDING = "onboarding_done"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private var prefs: SharedPreferences? = null

    /** Keypad touch tone. Backed by compose state so screens recompose when it changes. */
    var dtmfEnabled by mutableStateOf(true)
        private set

    /** Follow the wallpaper colors (Material You) instead of the app palette. Off by default. */
    var dynamicColor by mutableStateOf(false)
        private set

    /** Whether the first-run "make this your default phone app" screen has been dealt with. */
    var onboardingDone by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        dtmfEnabled = p.getBoolean(KEY_DTMF, true)
        onboardingDone = p.getBoolean(KEY_ONBOARDING, false)
        dynamicColor = p.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun updateDtmfEnabled(enabled: Boolean) {
        dtmfEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_DTMF, enabled)?.apply()
    }

    fun updateDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        prefs?.edit()?.putBoolean(KEY_DYNAMIC_COLOR, enabled)?.apply()
    }

    fun markOnboardingDone() {
        onboardingDone = true
        prefs?.edit()?.putBoolean(KEY_ONBOARDING, true)?.apply()
    }
}
