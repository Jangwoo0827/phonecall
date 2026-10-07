package com.example.superdialer.onboarding

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
            "기본 전화 앱으로 설정해 주세요",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "SuperDialer가 기본 전화 앱이 되면\n" +
                "· 수신·발신·통화 중 화면을 SuperDialer가 보여주고\n" +
                "· 잠금 화면에서도 전화를 받을 수 있으며\n" +
                "· 번호 차단을 사용할 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (denied) {
            Spacer(Modifier.height(16.dp))
            Text(
                "기본 전화 앱으로 설정하지 않았습니다. 설정 탭에서 언제든 다시 요청할 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = status.request, modifier = Modifier.fillMaxWidth()) {
            Text(if (denied) "다시 시도" else "기본 전화 앱으로 설정")
        }
        TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
            Text(if (denied) "계속" else "나중에")
        }
    }
}
