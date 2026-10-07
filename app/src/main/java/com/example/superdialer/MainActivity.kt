package com.example.superdialer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.superdialer.browser.BrowserViewModel
import com.example.superdialer.browser.UrlResolver
import com.example.superdialer.dialer.DialerViewModel
import com.example.superdialer.navigation.SuperDialerApp
import com.example.superdialer.navigation.TopLevelDestination
import com.example.superdialer.onboarding.DefaultDialerOnboarding
import com.example.superdialer.settings.AppSettings
import com.example.superdialer.ui.isDefaultDialer
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.theme.SuperDialerTheme

class MainActivity : ComponentActivity() {
    private val dialerViewModel: DialerViewModel by viewModels()
    private val browserViewModel: BrowserViewModel by viewModels()

    /** Tab requested by an external trigger (e.g. tapping a missed-call notification). */
    private var requestedRoute by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIntent(intent)
            askForNotificationPermission()
        }
        setContent {
            SuperDialerTheme {
                // First run: offer the default-phone-app role before showing the app.
                var onboarding by remember { mutableStateOf(!AppSettings.onboardingDone && !isDefaultDialer()) }
                if (onboarding) {
                    DefaultDialerOnboarding(onFinished = {
                        AppSettings.markOnboardingDone()
                        onboarding = false
                    })
                } else {
                    SuperDialerApp(
                        dialerViewModel = dialerViewModel,
                        requestedRoute = requestedRoute,
                        onRouteHandled = { requestedRoute = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (dialerViewModel.handleIntent(intent)) return
        // http(s) links, only offered by the optional ExternalLinkAlias (see Settings).
        val link = intent.data
        if (intent.action == Intent.ACTION_VIEW && link != null && UrlResolver.isWebScheme(link.scheme)) {
            browserViewModel.openExternal(link.toString())
            requestedRoute = TopLevelDestination.Hub.route
            return
        }
        val wantsCallLog = intent.getBooleanExtra(EXTRA_OPEN_CALL_LOG, false) ||
            (intent.action == Intent.ACTION_VIEW && intent.type == CallLog.Calls.CONTENT_TYPE)
        if (wantsCallLog) {
            requestedRoute = TopLevelDestination.CallLog.route
        }
    }

    /** Incoming-call and missed-call notifications need this on Android 13+. */
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_CALL_LOG = "open_call_log"
    }
}
