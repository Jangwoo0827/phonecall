package com.example.superdialer

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * The UI tests look for the Korean texts, whatever language the emulator or the CI machine is set to. The app reads its
 * language when its process starts, so it is written here, before the Application is created.
 */
class SuperDialerTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString("language", "ko").commit()
        super.callApplicationOnCreate(app)
    }
}
