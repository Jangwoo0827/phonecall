package com.example.superdialer.browser

import android.Manifest
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONException
import org.json.JSONObject
import java.net.URI

/** What a website can ask the phone for through the browser. */
enum class SitePermission(val key: String, val label: String, val osPermissions: List<String>) {
    Camera("camera", "카메라", listOf(Manifest.permission.CAMERA)),
    Microphone("mic", "마이크", listOf(Manifest.permission.RECORD_AUDIO)),
    // Android lets the user pick "approximate" instead of "precise": either one is enough for a website.
    Location("location", "위치", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION));

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

enum class SiteDecision(val key: String, val label: String) {
    Allow("allow", "허용"), Deny("deny", "차단");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * Remembered answers to "this site wants your camera / microphone / location", per site (origin). Pages never get a
 * permission without the user's answer, and only secure pages (https, or localhost) may ask at all. Private tabs never
 * read or write this. Kept on this phone only. Call [init] once from the Application.
 */
object SitePermissions {
    private const val PREFS = "site_permissions"
    private const val KEY = "sites"
    private const val MAX_SITES = 200

    private var appContext: Context? = null

    /** origin -> (permission -> decision); observable by Compose. */
    private val sites = mutableStateMapOf<String, Map<SitePermission, SiteDecision>>()

    fun init(context: Context) {
        appContext = context.applicationContext
        sites.clear()
        sites.putAll(decode(appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)))
    }

    fun decision(origin: String, permission: SitePermission): SiteDecision? = sites[origin]?.get(permission)

    /** Origins that have at least one remembered answer, in alphabetical order. */
    val origins: List<String> get() = sites.keys.sorted()

    fun answers(origin: String): Map<SitePermission, SiteDecision> = sites[origin].orEmpty()

    fun set(origin: String, permissions: Collection<SitePermission>, decision: SiteDecision) = commit(withAnswer(snapshot(), origin, permissions, decision, MAX_SITES))

    /** Forgets the answers of one site; it will ask again. */
    fun reset(origin: String) = commit(snapshot() - origin)

    fun resetAll() = commit(emptyMap())

    fun resetPermission(origin: String, permission: SitePermission) {
        val rest = answers(origin) - permission
        commit(if (rest.isEmpty()) snapshot() - origin else snapshot() + (origin to rest))
    }

    private fun snapshot(): Map<String, Map<SitePermission, SiteDecision>> = LinkedHashMap(sites)

    private fun commit(updated: Map<String, Map<SitePermission, SiteDecision>>) {
        sites.clear()
        sites.putAll(updated)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(updated))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** "https://Example.com:443/page?x" -> "https://example.com"; null when it is not an http(s) address. */
    fun normalizeOrigin(raw: String): String? {
        val uri = try { URI(raw.trim()) } catch (e: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port" // an IPv6 host already comes with its brackets
    }

    /** Camera, microphone and location are only for secure pages: https, or this phone itself. */
    fun isSecureOrigin(origin: String): Boolean {
        val uri = try { URI(origin) } catch (e: Exception) { return false }
        if (uri.scheme.equals("https", ignoreCase = true)) return true
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("http", ignoreCase = true) && (host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1")
    }

    /** The Android permissions that are still missing for [permissions], given which ones [isGranted] says are granted. */
    fun missingOsPermissions(permissions: Collection<SitePermission>, isGranted: (String) -> Boolean): List<String> =
        permissions.flatMap { permission ->
            // one of the listed Android permissions is enough (precise or approximate location)
            if (permission.osPermissions.any(isGranted)) emptyList() else permission.osPermissions
        }.distinct()

    /** The site permissions that [isGranted] allows through Android's own permissions. */
    fun grantedByOs(permissions: Collection<SitePermission>, isGranted: (String) -> Boolean): List<SitePermission> =
        permissions.filter { p -> p.osPermissions.any(isGranted) }

    fun withAnswer(
        all: Map<String, Map<SitePermission, SiteDecision>>,
        origin: String,
        permissions: Collection<SitePermission>,
        decision: SiteDecision,
        max: Int,
    ): Map<String, Map<SitePermission, SiteDecision>> {
        if (permissions.isEmpty()) return all
        val result = LinkedHashMap(all)
        val merged = (result.remove(origin).orEmpty()) + permissions.associateWith { decision }
        result[origin] = merged // re-inserted last: the most recently changed site stays when the list is trimmed
        while (result.size > max) result.remove(result.keys.first())
        return result
    }

    fun encode(all: Map<String, Map<SitePermission, SiteDecision>>): String = JSONObject().also { root ->
        all.forEach { (origin, answers) ->
            root.put(origin, JSONObject().also { o -> answers.forEach { (p, d) -> o.put(p.key, d.key) } })
        }
    }.toString()

    fun decode(text: String?): Map<String, Map<SitePermission, SiteDecision>> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(text)
            val out = LinkedHashMap<String, Map<SitePermission, SiteDecision>>()
            root.keys().forEach { origin ->
                val o = root.optJSONObject(origin) ?: return@forEach
                val answers = LinkedHashMap<SitePermission, SiteDecision>()
                o.keys().forEach { k ->
                    val p = SitePermission.fromKey(k)
                    val d = SiteDecision.fromKey(o.optString(k))
                    if (p != null && d != null) answers[p] = d
                }
                if (answers.isNotEmpty()) out[origin] = answers
            }
            out
        } catch (e: JSONException) {
            emptyMap()
        }
    }
}
