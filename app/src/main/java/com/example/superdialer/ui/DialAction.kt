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
fun rememberDialAction(): (String) -> Unit = rememberCallAction(video = false)

/** Like [rememberDialAction] but starts a video call; that also needs the camera permission. */
@Composable
fun rememberVideoDialAction(): (String) -> Unit = rememberCallAction(video = true)

@Composable
private fun rememberCallAction(video: Boolean): (String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }
    val needed = if (video) arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.CALL_PHONE)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val number = pending
        pending = null
        val allGranted = needed.all { results[it] == true || context.hasPermission(it) }
        if (allGranted && number != null) {
            place(context, number, video)
        } else {
            val what = if (video && context.hasPermission(Manifest.permission.CALL_PHONE)) "카메라" else "전화"
            Toast.makeText(context, "${if (video) "영상통화" else "전화"}를 걸려면 '$what' 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    return remember(context, video) {
        { number: String ->
            if (number.isNotEmpty()) {
                if (needed.all { context.hasPermission(it) }) {
                    place(context, number, video)
                } else {
                    pending = number
                    launcher.launch(needed)
                }
            }
        }
    }
}

private fun place(context: Context, number: String, video: Boolean) {
    val message = when (CallPlacer.placeCall(context, number, video)) {
        PlaceCallResult.Placed -> return
        PlaceCallResult.NoPermission -> "전화를 걸려면 '전화' 권한이 필요합니다."
        PlaceCallResult.Unavailable -> "이 기기에서는 전화를 걸 수 없습니다."
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
