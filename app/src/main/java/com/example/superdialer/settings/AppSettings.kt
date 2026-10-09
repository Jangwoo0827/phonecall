package com.example.superdialer.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Light/dark choice for the app; SYSTEM follows the phone. [key] is what is stored and synced. */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "시스템"), LIGHT("light", "라이트"), DARK("dark", "다크");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** App text size on top of the phone's own font size setting. */
enum class TextSize(val key: String, val label: String, val scale: Float) {
    SMALL("small", "작게", 0.9f), NORMAL("normal", "보통", 1f), LARGE("large", "크게", 1.15f), HUGE("huge", "최대", 1.3f);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}

/** Accent color of the app palette. */
enum class AccentColor(val key: String, val label: String) {
    GREEN("green", "초록"), BLUE("blue", "파랑"), PURPLE("purple", "보라"), ORANGE("orange", "주황"), PINK("pink", "분홍");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

/** Small persisted settings holder. Call [init] once from the Application. */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_DTMF = "dtmf_enabled"
    private const val KEY_ONBOARDING = "onboarding_done"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT = "accent"
    private const val KEY_QUICK_DIRECT = "quick_dial_direct"
    private const val KEY_AUTO_UPDATE = "auto_download_updates"
    private const val KEY_TEXT_SIZE = "text_size"

    private var prefs: SharedPreferences? = null

    /** Keypad touch tone. Backed by compose state so screens recompose when it changes. */
    var dtmfEnabled by mutableStateOf(true)
        private set

    /** Follow the wallpaper colors (Material You) instead of the app palette. Off by default. */
    var dynamicColor by mutableStateOf(false)
        private set

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var accent by mutableStateOf(AccentColor.GREEN)
        private set

    var textSize by mutableStateOf(TextSize.NORMAL)
        private set

    /** Fetch a newer release in the background (on Wi-Fi) so installing it is one tap. */
    var autoDownloadUpdates by mutableStateOf(false)
        private set

    /** Long-pressing an assigned keypad digit places the call at once instead of only filling in the number. */
    var quickDialCallsDirectly by mutableStateOf(false)
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
        themeMode = ThemeMode.fromKey(p.getString(KEY_THEME_MODE, null))
        accent = AccentColor.fromKey(p.getString(KEY_ACCENT, null))
        quickDialCallsDirectly = p.getBoolean(KEY_QUICK_DIRECT, false)
        autoDownloadUpdates = p.getBoolean(KEY_AUTO_UPDATE, false)
        textSize = TextSize.fromKey(p.getString(KEY_TEXT_SIZE, null))
    }

    fun updateTextSize(size: TextSize) {
        textSize = size
        prefs?.edit()?.putString(KEY_TEXT_SIZE, size.key)?.apply()
    }

    fun updateAutoDownloadUpdates(enabled: Boolean) {
        autoDownloadUpdates = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_UPDATE, enabled)?.apply()
    }

    fun updateQuickDialCallsDirectly(enabled: Boolean) {
        quickDialCallsDirectly = enabled
        prefs?.edit()?.putBoolean(KEY_QUICK_DIRECT, enabled)?.apply()
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.key)?.apply()
    }

    fun updateAccent(color: AccentColor) {
        accent = color
        prefs?.edit()?.putString(KEY_ACCENT, color.key)?.apply()
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
