package com.example.superdialer

import android.app.Application
import com.example.superdialer.incall.CallNotifications
import com.example.superdialer.settings.AppSettings
import com.example.superdialer.settings.RejectMessageStore

class SuperDialerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        RejectMessageStore.init(this)
        CallNotifications.ensureChannels(this)
    }
}
