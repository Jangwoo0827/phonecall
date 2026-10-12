package com.example.superdialer.messages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The pictures and files of one multimedia message, shown inside its bubble. A tap on a picture opens it large. */
@Composable
internal fun MmsAttachments(attachments: List<MmsAttachment>) {
    var opened by remember { mutableStateOf<MmsAttachment?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
        attachments.forEach { attachment ->
            if (attachment.isImage) {
                MmsPicture(attachment, maxPx = THUMB_PX, modifier = Modifier.heightIn(max = 220.dp).clickable { opened = attachment })
            } else {
                Text(
                    "첨부 · ${attachment.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    opened?.let { attachment ->
        Dialog(onDismissRequest = { opened = null }) {
            Box(modifier = Modifier.fillMaxWidth().clickable { opened = null }, contentAlignment = Alignment.Center) {
                MmsPicture(attachment, maxPx = FULL_PX, modifier = Modifier.fillMaxWidth(), fit = true)
            }
        }
    }
}

@Composable
private fun MmsPicture(attachment: MmsAttachment, maxPx: Int, modifier: Modifier = Modifier, fit: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, attachment.partId, maxPx) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmap(context, attachment.uri, maxPx) }
    }
    val loaded = bitmap
    if (loaded == null) {
        Text("사진을 불러오는 중…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "받은 사진",
            contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(12.dp)),
        )
    }
}

private const val THUMB_PX = 720
private const val FULL_PX = 2048

/** Decodes the picture at [uri] no larger than about [maxPx] on its long side (null when it cannot be read). Blocking I/O. */
internal fun decodeSampledBitmap(context: Context, uri: Uri, maxPx: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx) }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
} catch (e: Exception) {
    null
} catch (e: OutOfMemoryError) {
    null
}

/** Power-of-two downscale factor so that the long side ends up at most [maxPx]. */
internal fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
    var size = 1
    var longSide = maxOf(width, height)
    while (longSide / 2 >= maxPx) {
        longSide /= 2
        size *= 2
    }
    return size
}
