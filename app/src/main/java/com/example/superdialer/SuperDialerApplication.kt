package com.example.superdialer

import com.example.superdialer.crash.CrashLog
import com.example.superdialer.messages.SentSms
import com.example.superdialer.contacts.ContactGroups
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
        CrashLog.install(this)
        AppSettings.init(this)
        RejectMessageStore.init(this)
        GameScores.init(this)
        GameStates.init(this)
        AccountManager.init(this)
        QuickDials.init(this)
        CallNotes.init(this)
        ContactGroups.init(this)
        SentSms.init(this)
        CallNotifications.ensureChannels(this)
    }
}
