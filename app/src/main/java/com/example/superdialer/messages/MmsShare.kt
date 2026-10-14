package com.example.superdialer.messages

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/**
 * Sends a picture or video as an MMS **through the phone's own messaging app**: the file is copied into this app's
 * cache, and the system message composer is opened with the recipient and the attachment filled in; the user presses
 * send there. This app never becomes the default SMS app, so text messages, MMS and RCS (채팅+) keep arriving in the
 * phone's own app exactly as before. (Sending an MMS straight from here would need the default-SMS role.)
 */
object MmsShare {
    /** Carriers refuse large MMS anyway (the messaging app shrinks pictures); this just stops absurd copies. */
    const val MAX_BYTES = 25L * 1024 * 1024
    private const val DIR = "outgoing"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    class Staged(val uri: Uri, val mimeType: String)

    class TooLargeException : IOException("file too large")

    /** Copies [source] (a picked picture/video) into the cache and returns a shareable address. Blocking I/O. */
    fun stage(context: Context, source: Uri, now: Long = System.currentTimeMillis()): Staged {
        val resolver = context.contentResolver
        val mime = resolver.getType(source)?.takeIf { it.contains('/') } ?: "application/octet-stream"
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.filter { now - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() }

        val target = File(dir, stagedName(mime, now))
        try {
            val input = resolver.openInputStream(source) ?: throw IOException("cannot open $source")
            input.use { stream ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        total += read
                        if (isTooLarge(total)) throw TooLargeException()
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        return Staged(uri, mime)
    }

    /** The message composer intent for [number] with [staged] attached and [text] as the message text. */
    fun buildIntent(number: String, staged: Staged, text: String, targetPackage: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = staged.mimeType
            putExtra(Intent.EXTRA_STREAM, staged.uri)
            // the recipient and the text, under the names the messaging apps read
            putExtra("address", number)
            putExtra("sms_body", text)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra("exit_on_sent", true)
            clipData = ClipData.newRawUri("", staged.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (targetPackage != null) setPackage(targetPackage)
        }

    /** Opens the phone's message composer. Returns false when no app could take it. */
    fun send(context: Context, number: String, staged: Staged, text: String): Boolean {
        val messagingApp = Telephony.Sms.getDefaultSmsPackage(context)
        return try {
            context.startActivity(buildIntent(number, staged, text, messagingApp))
            true
        } catch (e: ActivityNotFoundException) {
            // no messaging app answered to its package: let the user pick any app that can take it
            try {
                val chooser = Intent.createChooser(buildIntent(number, staged, text, null), "보낼 앱 선택")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                true
            } catch (e2: ActivityNotFoundException) {
                false
            }
        }
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    fun isTooLarge(bytes: Long): Boolean = bytes > MAX_BYTES

    /** A file extension for a media type (a wrong guess only affects the name the messaging app shows). */
    fun extensionFor(mimeType: String): String = when (mimeType.lowercase().substringBefore(';').trim()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        "video/mp4" -> "mp4"
        "video/3gpp" -> "3gp"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        else -> "bin"
    }

    /** "mms_<time>.<ext>": no characters from the original name, so nothing odd ends up in a path. */
    fun stagedName(mimeType: String, nowMs: Long): String = "mms_$nowMs.${extensionFor(mimeType)}"
}
