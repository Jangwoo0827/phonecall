package com.example.superdialer.browser.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Site icons (favicons) for the start page, tabs, bookmarks and history.
 *
 * Icons come straight from the site itself (its `<link rel="icon">`, else `/favicon.ico`) and are cached on
 * disk per host. Nothing is sent to a third-party icon service. Pages the user visits also deliver their
 * icon through the WebView ([put]).
 */
class FaviconStore private constructor(context: Context) {
    private val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val memory = LruCache<String, Bitmap>(64)

    /** The cached icon for [url]'s host, or null (no network). Cheap enough for composition. */
    fun peek(url: String): Bitmap? {
        val host = hostOf(url) ?: return null
        memory.get(host)?.let { return it }
        val file = iconFile(host)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.path)?.also { memory.put(host, it) }
    }

    /** Cached icon, or downloads it. Null when the site has none we can decode. */
    suspend fun load(url: String): Bitmap? {
        peek(url)?.let { return it }
        val host = hostOf(url) ?: return null
        val miss = missFile(host)
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS) return null
        return withContext(Dispatchers.IO) { download(url, host) }
    }

    /** Stores an icon the WebView reported for a page we are showing. */
    fun put(url: String, icon: Bitmap) {
        val host = hostOf(url) ?: return
        if (icon.width < MIN_SIZE) return
        // The WebView's icon is often tiny; do not replace a sharper one we already have.
        if ((peek(url)?.width ?: 0) >= icon.width) return
        memory.put(host, icon)
        save(host, icon)
    }

    private fun download(url: String, host: String): Bitmap? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
        val base = "$scheme://${uri.rawAuthority}/"

        val html = fetch(base, MAX_HTML_BYTES)?.toString(Charsets.UTF_8)
        val candidates = listOfNotNull(html?.let { pickIconUrl(it, base) }, base + "favicon.ico").distinct()
        for (candidate in candidates) {
            val bytes = fetch(candidate, MAX_ICON_BYTES) ?: continue
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            if (bitmap.width < MIN_SIZE) continue
            memory.put(host, bitmap)
            save(host, bitmap)
            return bitmap
        }
        missFile(host).apply { writeText("") ; setLastModified(System.currentTimeMillis()) }
        return null
    }

    private fun save(host: String, bitmap: Bitmap) {
        runCatching {
            iconFile(host).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            missFile(host).delete()
        }
    }

    /** GET with a size cap and a few redirects (HttpURLConnection will not follow http -> https by itself). */
    private fun fetch(address: String, maxBytes: Int): ByteArray? {
        var current = address
        repeat(MAX_REDIRECTS + 1) {
            val connection = runCatching { URL(current).openConnection() as HttpURLConnection }.getOrNull() ?: return null
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = runCatching { URI(current).resolve(location).toString() }.getOrNull() ?: return null
                    if (!current.startsWith("http://") && !current.startsWith("https://")) return null
                    return@repeat
                }
                if (code != 200) return null
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                connection.inputStream.use { input ->
                    while (out.size() < maxBytes) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                    }
                }
                return out.toByteArray()
            } catch (e: java.io.IOException) {
                return null
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun iconFile(host: String) = File(dir, "${safeName(host)}.png")
    private fun missFile(host: String) = File(dir, "${safeName(host)}.none")

    companion object {
        private const val MIN_SIZE = 16
        private const val TIMEOUT_MS = 5_000
        private const val MAX_REDIRECTS = 3
        private const val MAX_HTML_BYTES = 200_000
        private const val MAX_ICON_BYTES = 512_000
        private const val MISS_TTL_MS = 3L * 24 * 60 * 60 * 1000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        @Volatile private var instance: FaviconStore? = null

        fun get(context: Context): FaviconStore = instance ?: synchronized(this) {
            instance ?: FaviconStore(context.applicationContext).also { instance = it }
        }

        fun hostOf(url: String): String? =
            runCatching { URI(url).host }.getOrNull()?.lowercase()?.takeIf { it.isNotBlank() }

        private fun safeName(host: String) = host.replace(Regex("[^a-z0-9.-]"), "_")

        private val linkTag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
        private val relAttr = Regex("""\brel\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val hrefAttr = Regex("""\bhref\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val sizesAttr = Regex("""\bsizes\s*=\s*["'](\d+)x\d+["']""", RegexOption.IGNORE_CASE)

        /**
         * The best icon URL declared in [html]: prefers an apple-touch-icon (large, crisp), then the biggest
         * declared size. SVG and data: icons are skipped. Relative links are resolved against [baseUrl].
         */
        fun pickIconUrl(html: String, baseUrl: String): String? {
            data class Candidate(val href: String, val score: Int)
            val candidates = linkTag.findAll(html).mapNotNull { match ->
                val tag = match.value
                val rel = relAttr.find(tag)?.groupValues?.get(1)?.lowercase()?.split(Regex("\\s+")).orEmpty()
                if (rel.none { it == "icon" || it.startsWith("apple-touch-icon") }) return@mapNotNull null
                val href = hrefAttr.find(tag)?.groupValues?.get(1)?.trim().orEmpty()
                if (href.isEmpty() || href.startsWith("data:") || href.substringBefore('?').endsWith(".svg", ignoreCase = true)) {
                    return@mapNotNull null
                }
                val size = sizesAttr.find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val apple = if (rel.any { it.startsWith("apple-touch-icon") }) 10_000 else 0
                Candidate(href, apple + size)
            }.toList()
            val best = candidates.maxByOrNull { it.score } ?: return null
            return runCatching { URI(baseUrl).resolve(best.href).toString() }.getOrNull()
        }
    }
}
