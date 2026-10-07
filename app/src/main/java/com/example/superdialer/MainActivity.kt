package com.example.superdialer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.superdialer.dialer.DialerViewModel
import com.example.superdialer.navigation.SuperDialerApp
import com.example.superdialer.navigation.TopLevelDestination
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.theme.SuperDialerTheme

class MainActivity : ComponentActivity() {
    private val dialerViewModel: DialerViewModel by viewModels()

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
                SuperDialerApp(
                    dialerViewModel = dialerViewModel,
                    requestedRoute = requestedRoute,
                    onRouteHandled = { requestedRoute = null },
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
        if (dialerViewModel.handleIntent(intent)) return
        if (intent.getBooleanExtra(EXTRA_OPEN_CALL_LOG, false)) {
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
