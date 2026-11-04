package com.example.superdialer

import com.example.superdialer.kakao.KakaoInbox
import com.example.superdialer.update.WhatsNew
import com.example.superdialer.crash.CrashLog
import com.example.superdialer.messages.SentSms
import com.example.superdialer.contacts.ContactGroups
import com.example.superdialer.contacts.ContactMemos
import com.example.superdialer.calllog.CallNotes
import com.example.superdialer.dialer.QuickDials
import android.app.Application
import com.example.superdialer.account.AccountManager
import com.example.superdialer.games.GameScores
import com.example.superdialer.games.GameStates
import com.example.superdialer.incall.CallNotifications
import com.example.superdialer.settings.AppSettings
import com.example.superdialer.settings.RejectMessageStore

class SuperDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // First of all: the texts. Classes such as the settings enums build their labels the moment they are first used
        // (AppSettings.init below already does), so the language must be applied before anything else runs.
        com.example.superdialer.i18n.Strings.init(
            this,
            com.example.superdialer.i18n.AppLanguage.fromKey(getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", null)),
        )
        AppSettings.init(this)
        CrashLog.install(this)
        WhatsNew.check(this)
        RejectMessageStore.init(this)
        GameScores.init(this)
        GameStates.init(this)
        AccountManager.init(this)
        QuickDials.init(this)
        CallNotes.init(this)
        ContactMemos.init(this)
        com.example.superdialer.browser.SitePermissions.init(this)
        ContactGroups.init(this)
        SentSms.init(this)
        KakaoInbox.init(this)
        CallNotifications.ensureChannels(this)
    }
}
