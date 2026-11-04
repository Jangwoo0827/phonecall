package com.example.superdialer.ui

import com.example.superdialer.R
import com.example.superdialer.i18n.str
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
            val what = if (video && context.hasPermission(Manifest.permission.CALL_PHONE)) str(R.string.t_camera) else str(R.string.t_call)
            Toast.makeText(context, str(if (video) R.string.permission_needed_video else R.string.permission_needed_call, what), Toast.LENGTH_SHORT).show()
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
        PlaceCallResult.NoPermission -> str(R.string.t_calling_needs_the_phone_permission)
        PlaceCallResult.Unavailable -> str(R.string.t_this_device_can_t_place_calls)
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
