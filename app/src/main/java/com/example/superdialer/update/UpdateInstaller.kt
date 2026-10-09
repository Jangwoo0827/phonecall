package com.example.superdialer.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a new release APK from this project's GitHub Releases and hands it to the system installer.
 *
 * Safety: only addresses under this repository's releases are downloaded; the file must be this app (same package
 * name), newer than what is installed, and signed with the same certificate (the installer refuses anything else, we
 * check first only to give a clear message). Android asks the user to confirm an install; once this app has installed
 * an update itself, Android 12+ may later update it without asking.
 */
object UpdateInstaller {
    private const val ALLOWED_PREFIX = "https://github.com/Jangwoo0827/phonecall/releases/download/"
    private const val DIR = "updates"

    /** Intent action of the install result broadcast ([InstallStatusReceiver]). */
    const val ACTION_STATUS = "com.example.superdialer.INSTALL_STATUS"

    fun isAllowedUrl(url: String): Boolean = url.startsWith(ALLOWED_PREFIX)

    fun apkFile(context: Context, version: String): File =
        File(File(context.cacheDir, DIR).apply { mkdirs() }, "SuperDialer-${version.filter { it.isLetterOrDigit() || it == '.' }}.apk")

    /** Deletes downloaded installers other than [keepVersion] (or all when null). */
    fun cleanUp(context: Context, keepVersion: String? = null) {
        val keep = keepVersion?.let { apkFile(context, it).name }
        File(context.cacheDir, DIR).listFiles()?.filter { it.name != keep }?.forEach { it.delete() }
    }

    /** Whether the user has allowed this app to install packages (a system setting per app). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** True on Wi-Fi / unmetered data, so a large download does not eat mobile data. */
    fun isUnmetered(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Blocking download to [apkFile]; [onProgress] gets 0..100 (or -1 when the size is unknown). Throws IOException. */
    fun download(context: Context, info: ReleaseInfo, onProgress: (Int) -> Unit): File {
        val url = info.apkUrl ?: throw IOException("릴리스에 APK가 없습니다.")
        if (!isAllowedUrl(url)) throw IOException("허용되지 않는 다운로드 주소입니다.")
        val target = apkFile(context, info.version)
        val partial = File(target.path + ".part")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "SuperDialer")
            if (connection.responseCode != 200) throw IOException("받지 못했습니다 (${connection.responseCode}).")
            val total = connection.contentLengthLong
            var done = 0L
            var lastPercent = -2
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != lastPercent) { lastPercent = percent; onProgress(percent) }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("다운로드가 끊겼습니다.")
            if (!partial.renameTo(target)) throw IOException("파일을 저장하지 못했습니다.")
            return target
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    /** Null when [file] is a newer SuperDialer signed like the installed one; otherwise a message saying why not. */
    fun verify(context: Context, file: File): String? {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(file.path, flags) ?: return "받은 파일을 읽을 수 없습니다."
        if (archive.packageName != context.packageName) return "SuperDialer 설치 파일이 아닙니다."
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.longVersionCode <= installed.longVersionCode) return "이미 같거나 더 새로운 버전이 설치되어 있습니다."
        val newSigners = archive.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }.orEmpty().toSet()
        val oldSigners = installed.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }.orEmpty().toSet()
        if (newSigners.isEmpty() || newSigners != oldSigners) {
            return "설치된 앱과 서명이 달라 덮어 설치할 수 없습니다. (adb로 깐 디버그 빌드라면 앱을 지우고 릴리스 APK를 설치해야 합니다)"
        }
        return null
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Hands [file] to the system installer. The result arrives at [InstallStatusReceiver]. */
    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Lets Android skip the confirmation when it is allowed to (this app installed the current version).
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("SuperDialer.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val intent = Intent(ACTION_STATUS).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's progress: opens its confirmation screen when needed and reports failures. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateChecker.onInstallFinished(null)
            else -> UpdateChecker.onInstallFinished(failureText(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
        }
    }

    private fun failureText(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "설치를 취소했습니다."
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "설치된 앱과 서명이 달라 덮어 설치할 수 없습니다. 디버그 빌드라면 앱을 지우고 릴리스 APK를 설치해 주세요."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "저장 공간이 부족합니다."
        else -> "설치하지 못했습니다${if (detail.isNullOrBlank()) "" else " ($detail)"}."
    }
}
