package com.example.superdialer.ui

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.superdialer.dialer.CallPlacer
import com.example.superdialer.dialer.PlaceCallResult

/** Returns a function that places a call, asking for CALL_PHONE first if needed. */
@Composable
fun rememberDialAction(): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val number = pending
        pending = null
        if (granted && number != null) {
            place(context, number)
        } else {
            Toast.makeText(context, "전화를 걸려면 '전화' 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    return remember(context) {
        { number: String ->
            if (number.isNotEmpty()) {
                if (context.hasPermission(Manifest.permission.CALL_PHONE)) {
                    place(context, number)
                } else {
                    pending = number
                    launcher.launch(Manifest.permission.CALL_PHONE)
                }
            }
        }
    }
}

private fun place(context: Context, number: String) {
    val message = when (CallPlacer.placeCall(context, number)) {
        PlaceCallResult.Placed -> return
        PlaceCallResult.NoPermission -> "전화를 걸려면 '전화' 권한이 필요합니다."
        PlaceCallResult.Unavailable -> "이 기기에서는 전화를 걸 수 없습니다."
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
