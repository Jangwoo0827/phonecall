package com.example.superdialer.i18n

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import com.example.superdialer.settings.AppSettings

/** Applies a new language: saves it, tells the system (so the app name and shortcuts follow), and restarts the app. */
object LanguageSwitcher {
    fun change(activity: Activity, language: AppLanguage) {
        if (language == AppSettings.language) return
        AppSettings.updateLanguage(language)
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val manager = activity.getSystemService(android.app.LocaleManager::class.java)
                manager.applicationLocales = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
            } catch (e: Exception) {
                // the system part is a nicety; the app's own texts switch with the restart below
            }
        }
        restart(activity)
    }

    /** A fresh process, so texts that objects kept from their first use are rebuilt in the new language. */
    private fun restart(activity: Activity) {
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        activity.startActivity(launch)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
