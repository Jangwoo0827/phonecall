package com.example.superdialer.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The music files on the phone (MediaStore). They play in full length, unlike the 30-second online previews. */
object LocalMusic {
    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    suspend fun load(context: Context): List<Track> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val out = ArrayList<Track>()
        val unknown = str(R.string.music_unknown_artist)
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.DURATION,
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val artist = c.getString(2)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: unknown
                    val album = c.getString(3)?.takeIf { it.isNotBlank() && it != "<unknown>" }.orEmpty()
                    out.add(
                        Track(
                            id = "lc:$id",
                            title = c.getString(1).orEmpty().ifEmpty { "?" },
                            artist = artist,
                            artistId = 0L,
                            album = album,
                            albumId = c.getLong(4),
                            artUrl = uri,
                            durationSec = (c.getLong(5) / 1000).toInt(),
                            uri = uri,
                            source = TrackSource.LOCAL,
                        ),
                    )
                }
            }
        }
        out
    }
}
