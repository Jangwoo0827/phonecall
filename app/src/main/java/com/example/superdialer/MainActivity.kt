package com.example.superdialer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.superdialer.navigation.SuperDialerApp
import com.example.superdialer.ui.theme.SuperDialerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SuperDialerTheme {
                SuperDialerApp()
            }
        }
    }
}
