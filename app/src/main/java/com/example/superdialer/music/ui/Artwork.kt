package com.example.superdialer.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.superdialer.music.artAtSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Album and artist pictures: downloaded once, kept in memory (LruCache) and on disk (cacheDir/music_art), no image
 * library. Pictures of music files on the phone come from the system thumbnail of the file (content:// URIs).
 */
object ArtCache {
    private val memory = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 1024 / 10).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4 / 1024
    }

    private fun bucket(px: Int) = when {
        px <= 150 -> 120
        px <= 300 -> 250
        px <= 600 -> 500
        else -> 1000
    }

    private fun resolve(url: String, px: Int): String = if (url.startsWith("http")) artAtSize(url, bucket(px)) ?: url else url

    fun peek(url: String?, px: Int): ImageBitmap? = url?.let { memory.get(resolve(it, px)) }

    suspend fun get(context: Context, url: String, px: Int): ImageBitmap? {
        val key = resolve(url, px)
        memory.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) { load(context.applicationContext, key, px) } ?: return null
        val image = bmp.asImageBitmap()
        memory.put(key, image)
        return image
    }

    private fun load(context: Context, key: String, px: Int): Bitmap? = runCatching {
        if (key.startsWith("content://")) {
            context.contentResolver.loadThumbnail(Uri.parse(key), Size(bucket(px), bucket(px)), null)
        } else {
            val dir = File(context.cacheDir, "music_art").apply { mkdirs() }
            val file = File(dir, sha1(key))
            if (!file.exists()) {
                val conn = URL(key).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                try {
                    if (conn.responseCode !in 200..299) return@runCatching null
                    file.writeBytes(conn.inputStream.use { it.readBytes() })
                    prune(dir)
                } finally {
                    conn.disconnect()
                }
            }
            BitmapFactory.decodeFile(file.path)
        }
    }.getOrNull()

    private fun prune(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size > 400) files.sortedBy { it.lastModified() }.take(files.size - 300).forEach { it.delete() }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The picture's average color (for the Now Playing background). */
    fun averageColor(image: ImageBitmap): Color {
        val small = Bitmap.createScaledBitmap(image.asAndroidBitmap(), 1, 1, true)
        val p = small.getPixel(0, 0)
        return Color(android.graphics.Color.red(p), android.graphics.Color.green(p), android.graphics.Color.blue(p))
    }

}

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    px: Int = 250,
    shape: Shape = RoundedCornerShape(6.dp),
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val image by produceState(initialValue = ArtCache.peek(url, px), url, px) {
        value = if (url == null) null else ArtCache.peek(url, px) ?: ArtCache.get(context, url, px)
    }
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = image
        if (bmp != null) {
            Image(bmp, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** The average color of a picture, or null until it is loaded. */
@Composable
fun rememberArtColor(url: String?, px: Int = 250): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(initialValue = null, url) {
        value = if (url == null) null else ArtCache.get(context, url, px)?.let { ArtCache.averageColor(it) }
    }
    return remember(color) { color }
}
