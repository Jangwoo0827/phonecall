package com.example.superdialer.update

import com.example.superdialer.settings.AppSettings
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

    /** The new APK is being downloaded ([percent] -1 when the size is unknown). */
    data class Downloading(val info: ReleaseInfo, val percent: Int) : UpdateState

    /** The new APK is downloaded and checked; installing is one tap. */
    data class Ready(val info: ReleaseInfo) : UpdateState

    /** Handed to the system installer, which may show its own confirmation. */
    data class Installing(val info: ReleaseInfo) : UpdateState
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
    private var appContext: Context? = null

    /** Observable by Compose. */
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** The release being downloaded or installed, kept so a failure can offer it again. */
    private var working: ReleaseInfo? = null

    /** Downloads the release (if needed) and installs it. Call from the UI after the user asked for it. */
    fun startUpdate(context: Context, info: ReleaseInfo) {
        val app = context.applicationContext
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return
        working = info
        scope.launch {
            val file = UpdateInstaller.apkFile(app, info.version)
            try {
                if (!file.exists()) {
                    withContext(Dispatchers.Main) { state = UpdateState.Downloading(info, 0) }
                    UpdateInstaller.download(app, info) { percent ->
                        scope.launch(Dispatchers.Main) { state = UpdateState.Downloading(info, percent) }
                    }
                }
                val problem = UpdateInstaller.verify(app, file)
                if (problem != null) {
                    file.delete()
                    withContext(Dispatchers.Main) { state = UpdateState.Failed(problem) }
                    return@launch
                }
                withContext(Dispatchers.Main) { state = UpdateState.Installing(info) }
                UpdateInstaller.install(app, file)
            } catch (e: Exception) {
                file.delete()
                withContext(Dispatchers.Main) { state = UpdateState.Failed(e.message ?: "업데이트하지 못했습니다.") }
            }
        }
    }

    /** Called by the installer's result receiver. */
    fun onInstallFinished(failure: String?) {
        scope.launch(Dispatchers.Main) {
            state = if (failure == null) UpdateState.Idle else UpdateState.Failed(failure)
        }
    }

    /** Manual check ("업데이트 확인"). */
    fun check(context: Context) {
        appContext = context.applicationContext
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
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK, 0L) < AUTO_INTERVAL_MS || state == UpdateState.Checking) return
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        val current = currentVersion(context)
        scope.launch {
            val result = fetch(current)
            if (result !is UpdateState.Available) return@launch
            withContext(Dispatchers.Main) { state = result }
            if (AppSettings.autoDownloadUpdates && result.info.apkUrl != null && UpdateInstaller.isUnmetered(context)) {
                predownload(context.applicationContext, result.info)
            }
        }
    }

    /** Fetches and checks the installer in the background; on success the update shows as ready to install. */
    private suspend fun predownload(app: Context, info: ReleaseInfo) {
        val file = UpdateInstaller.apkFile(app, info.version)
        try {
            if (!file.exists()) UpdateInstaller.download(app, info) { }
            if (UpdateInstaller.verify(app, file) == null) {
                UpdateInstaller.cleanUp(app, keepVersion = info.version)
                withContext(Dispatchers.Main) { state = UpdateState.Ready(info) }
                UpdateNotifications.showReady(app, info.version)
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            file.delete()
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
                    when {
                        !isNewer(release.version, current) -> UpdateState.UpToDate(current)
                        // an installer for this version was already downloaded and checked
                        UpdateInstaller.apkFile(appContext ?: return UpdateState.Available(release), release.version).exists() -> UpdateState.Ready(release)
                        else -> UpdateState.Available(release)
                    }
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
