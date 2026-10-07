package com.example.superdialer.incall

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.superdialer.MainActivity
import com.example.superdialer.ui.theme.SuperDialerTheme

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen for incoming calls.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // The in-call screen is always dark, so use light system bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            SuperDialerTheme(darkTheme = true) {
                InCallScreen(
                    onClose = { finish() },
                    onAddCall = {
                        startActivity(
                            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_ANSWER, false)) {
            CallManager.answerRinging()
            intent.removeExtra(EXTRA_ANSWER)
        }
    }

    companion object {
        private const val EXTRA_ANSWER = "answer"

        fun intent(context: Context, answer: Boolean = false): Intent =
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_ANSWER, answer)
    }
}
