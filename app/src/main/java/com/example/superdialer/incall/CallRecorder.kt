package com.example.superdialer.incall

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Best-effort recording of a call through the microphone (needs RECORD_AUDIO).
 *
 * Android does not let ordinary apps capture the other person's voice from the call itself, so this only records
 * what the microphone hears: your own voice, and the other side's only when the speaker is on. The file goes to
 * Music/SuperDialer (visible in any file manager or music app). Recording may stop hearing anything when the screen
 * is off. Check the law about recording calls where you live before using it.
 */
object CallRecorder {
    /** Observable by Compose. */
    var recording by mutableStateOf(false)
        private set

    private var recorder: MediaRecorder? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var target: Uri? = null

    /** Starts recording; false when it could not be started. */
    fun start(context: Context, who: String): Boolean {
        if (recording) return true
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName(System.currentTimeMillis(), who))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SuperDialer")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val pfd = resolver.openFileDescriptor(uri, "w") ?: throw IOException("no descriptor")
            val rec = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(pfd.fileDescriptor)
                prepare()
                start()
            }
            recorder = rec
            descriptor = pfd
            target = uri
            recording = true
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            cleanup()
            false
        }
    }

    /** Stops and finishes the file. Safe to call when nothing is recording. */
    fun stop(context: Context) {
        val rec = recorder ?: return
        val uri = target
        try {
            rec.stop()
        } catch (e: RuntimeException) {
            // stopped right after starting: nothing was recorded
            uri?.let { context.contentResolver.delete(it, null, null) }
            cleanup()
            return
        }
        cleanup()
        uri?.let {
            context.contentResolver.update(it, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        }
    }

    private fun cleanup() {
        runCatching { recorder?.release() }
        runCatching { descriptor?.close() }
        recorder = null
        descriptor = null
        target = null
        recording = false
    }

    fun fileName(atMillis: Long, who: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(atMillis))
        val safe = who.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(20)
        return "통화_${stamp}${if (safe.isEmpty()) "" else "_$safe"}.m4a"
    }
}
