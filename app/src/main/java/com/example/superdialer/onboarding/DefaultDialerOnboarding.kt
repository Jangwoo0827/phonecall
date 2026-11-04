package com.example.superdialer.onboarding

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.superdialer.ui.rememberDefaultDialerStatus

/**
 * First-run screen asking the user to make SuperDialer the default phone app.
 * [onFinished] is called once the user has granted the role or chosen to continue without it.
 */
@Composable
fun DefaultDialerOnboarding(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var denied by rememberSaveable { mutableStateOf(false) }
    val status = rememberDefaultDialerStatus { granted ->
        if (granted) onFinished() else denied = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Call,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            str(R.string.t_set_as_your_default_phone_app),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            str(R.string.t_when_superdialer_is_your_default_phone),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (denied) {
            Spacer(Modifier.height(16.dp))
            Text(
                str(R.string.t_superdialer_isn_t_your_default_phone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = status.request, modifier = Modifier.fillMaxWidth()) {
            Text(if (denied) str(R.string.t_try_again) else str(R.string.t_set_as_default_phone_app))
        }
        TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
            Text(if (denied) str(R.string.t_continue_text) else str(R.string.t_later))
        }
    }
}
