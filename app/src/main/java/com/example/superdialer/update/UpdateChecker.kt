package com.example.superdialer.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(val tag: String, val version: String, val pageUrl: String, val apkUrl: String?, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Looks for a newer SuperDialer on the GitHub Releases page (there is no app store). It only reads the public
 * "latest release" and never installs anything: the new APK is opened in the browser, the user installs it.
 */
object UpdateChecker {
    private const val LATEST_URL = "https://api.github.com/repos/Jangwoo0827/phonecall/releases/latest"
    private const val PREFS = "update_checker"
    private const val KEY_LAST_CHECK = "last_check"
    private const val AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Observable by Compose. */
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Manual check ("업데이트 확인"). */
    fun check(context: Context) {
        if (state == UpdateState.Checking) return
        state = UpdateState.Checking
        val current = currentVersion(context)
        scope.launch {
            val result = fetch(current)
            withContext(Dispatchers.Main) { state = result }
        }
    }

    /** At most once a day, quietly: only a found update is shown; failures and "up to date" leave the state alone. */
    fun checkInBackground(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_INTERVAL_MS || state == UpdateState.Checking) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val current = currentVersion(context)
        scope.launch {
            val result = fetch(current)
            if (result is UpdateState.Available) withContext(Dispatchers.Main) { state = result }
        }
    }

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    private fun fetch(current: String): UpdateState {
        val connection = try {
            URL(LATEST_URL).openConnection() as HttpURLConnection
        } catch (e: java.io.IOException) {
            return UpdateState.Failed("연결할 수 없습니다.")
        }
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "SuperDialer")
            when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val release = parseRelease(body) ?: return UpdateState.Failed("릴리스 정보를 읽을 수 없습니다.")
                    if (isNewer(release.version, current)) UpdateState.Available(release) else UpdateState.UpToDate(current)
                }
                404 -> UpdateState.Failed("아직 올라온 릴리스가 없습니다.")
                else -> UpdateState.Failed("확인하지 못했습니다 ($code).")
            }
        } catch (e: java.io.IOException) {
            UpdateState.Failed("인터넷에 연결할 수 없습니다.")
        } finally {
            connection.disconnect()
        }
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    fun parseRelease(json: String): ReleaseInfo? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        val assets = o.optJSONArray("assets")
        var apk: String? = null
        for (i in 0 until (assets?.length() ?: 0)) {
            val asset = assets!!.getJSONObject(i)
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apk = asset.optString("browser_download_url").ifEmpty { null }
                break
            }
        }
        ReleaseInfo(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            pageUrl = o.optString("html_url"),
            apkUrl = apk,
            notes = o.optString("body").trim(),
        )
    } catch (e: JSONException) {
        null
    }

    /** Dotted numbers compared piece by piece (0.10.0 > 0.9.0); anything after a `-` or `+` is ignored. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
