package com.example.superdialer.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/** The language of the app: follow the phone, or Korean / English whatever the phone says. [tag] is a BCP 47 tag (null = the phone's). */
enum class AppLanguage(val key: String, val tag: String?) {
    SYSTEM("system", null), KOREAN("ko", "ko"), ENGLISH("en", "en");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Every text the user sees comes from here ([str]), so the whole app switches language together. The language is applied once
 * when the process starts ([init], from the Application); changing it in the settings restarts the app, so texts that
 * objects keep from their first use never show the old language.
 */
object Strings {
    @Volatile
    private var localized: Context? = null

    /** The locale used for dates, numbers and the like. */
    @Volatile
    var locale: Locale = Locale.KOREAN
        private set

    fun init(app: Context, language: AppLanguage) {
        val base = app.applicationContext
        val tag = language.tag
        if (tag == null) {
            localized = base
            locale = base.resources.configuration.locales[0]
        } else {
            locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            localized = base.createConfigurationContext(config)
        }
    }

    /** True while the texts are Korean (for the few places that need different word order or date patterns). */
    val isKorean: Boolean get() = locale.language == "ko"

    /** Plain unit tests have no Android resources: they can install a resolver that reads the Korean texts (see TestStrings). */
    @Volatile
    var resolver: ((Int, Array<out Any>) -> String)? = null

    fun get(@StringRes id: Int, vararg args: Any): String {
        resolver?.let { return it(id, args) }
        val context = localized ?: return "#$id" + args.joinToString("") { ",$it" } // not started (plain unit tests)
        return context.getString(id, *args) // always formatted: "%%" in the resources stands for a plain "%"
    }
}

/** The text of string resource [id] in the app's language; [args] fill its %1$s, %2$d ... */
fun str(@StringRes id: Int, vararg args: Any): String = Strings.get(id, *args)
