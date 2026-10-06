package com.example.superdialer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.superdialer.dialer.DialerViewModel
import com.example.superdialer.navigation.SuperDialerApp
import com.example.superdialer.ui.theme.SuperDialerTheme

class MainActivity : ComponentActivity() {
    private val dialerViewModel: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) dialerViewModel.handleIntent(intent)
        setContent {
            SuperDialerTheme {
                SuperDialerApp(dialerViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dialerViewModel.handleIntent(intent)
    }
}
