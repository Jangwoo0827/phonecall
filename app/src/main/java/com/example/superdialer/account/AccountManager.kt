package com.example.superdialer.account

import org.json.JSONObject
import com.example.superdialer.settings.ThemeMode
import com.example.superdialer.settings.AccentColor
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import com.example.superdialer.browser.SpeedDialTree
import com.example.superdialer.browser.data.Bookmark
import com.example.superdialer.browser.data.BrowserDatabase
import com.example.superdialer.browser.data.SpeedDial
import com.example.superdialer.games.GameScores
import com.example.superdialer.settings.AppSettings
import com.example.superdialer.settings.RejectMessageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The optional SuperDialer account (Supabase email login) and the sync of start-page tiles, bookmarks,
 * game scores, settings and reject messages. Without an account nothing leaves the phone.
 *
 * Sync rule: the server keeps one snapshot per user. A device pulls when the server copy changed since its
 * last sync (and pushes instead when this device changed too), and pushes when only this device changed.
 * On the first sync of a device the server copy wins, except game scores, which keep the better value.
 */
object AccountManager {
    private const val PREFS = "account"
    private const val KEY_EMAIL = "email"
    private const val KEY_USER = "user_id"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_FINGERPRINT = "last_fingerprint"
    private const val KEY_STAMP = "last_stamp"
    private const val KEY_LAST_SYNC = "last_sync_ms"
    private const val KEY_CHECKLIST = "checklist_id"
    private const val KEY_NICKNAME = "nickname"
    private const val MIN_AUTO_SYNC_GAP_MS = 15_000L

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var lastAutoSync = 0L

    /** Observable by Compose. */
    var email by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** Result of the last action, shown under the account rows. */
    var message by mutableStateOf<String?>(null)
        private set
    var lastSyncMs by mutableStateOf(0L)
        private set

    val signedIn: Boolean get() = email != null

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        email = p.getString(KEY_EMAIL, null)
        lastSyncMs = p.getLong(KEY_LAST_SYNC, 0L)
    }

    /** The checklist site's own sync id that this account remembers, or null. */
    val checklistId: String? get() = prefs?.getString(KEY_CHECKLIST, null)?.takeIf { signedIn }

    /** Called when the checklist page in the browser shows which id it is logged in with. */
    fun rememberChecklistId(id: String) {
        if (!signedIn || !SyncSnapshot.isValidChecklistId(id) || id == checklistId) return
        prefs?.edit()?.putString(KEY_CHECKLIST, id)?.apply()
        syncSoon()
    }

    // --- Leaderboard ----------------------------------------------------------------------------

    /** The name shown on leaderboards: the chosen one, or "플레이어" + the first characters of the account id. */
    val nickname: String
        get() = prefs?.getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() }
            ?: "플레이어" + (prefs?.getString(KEY_USER, "").orEmpty().take(4))

    fun updateNickname(value: String) {
        val clean = value.trim().take(20)
        if (clean.isEmpty()) return
        prefs?.edit()?.putString(KEY_NICKNAME, clean)?.apply()
        nicknameVersion++
        // Renames the player on every score they already have.
        scope.launch { GameScores.all().forEach { (game, score) -> pushScore(game, score) } }
    }

    /** Bumped when the nickname changes, so screens showing it recompose. */
    var nicknameVersion by mutableStateOf(0)
        private set

    /** Sends a new best score to the leaderboard (does nothing when not signed in; failures are ignored, the next sync retries). */
    fun uploadScore(gameId: String, score: Int) {
        if (!signedIn || score <= 0) return
        scope.launch { pushScore(gameId, score) }
    }

    private fun pushScore(gameId: String, score: Int) {
        try {
            val session = freshSession() ?: return
            SupabaseApi.submitScore(session.accessToken, gameId, score, nickname)
        } catch (e: ApiError) {
            if (e.authExpired) scope.launch(Dispatchers.Main) { signOut(); message = e.userMessage }
        }
    }

    /** Loads a leaderboard in the background; [onResult] runs on the main thread with the rows, or null and an error text. */
    fun loadLeaderboard(gameId: String, onResult: (rows: List<LeaderRow>?, error: String?, myUserId: String?) -> Unit) {
        scope.launch {
            val result = try {
                val session = freshSession()
                if (session == null) Triple(null, "로그인이 필요합니다.", null)
                else Triple(SupabaseApi.fetchLeaderboard(session.accessToken, gameId), null, session.userId)
            } catch (e: ApiError) {
                Triple(null, e.userMessage, null)
            }
            withContext(Dispatchers.Main) { onResult(result.first, result.second, result.third) }
        }
    }

    /**
     * A login for the checklist page: the same account, handed over as a short-lived "managed" session (access token
     * only, no refresh token, at least 15 minutes left). [onResult] runs on the main thread; null when not signed in.
     */
    fun webSessionJson(onResult: (String?) -> Unit) {
        if (!signedIn) { onResult(null); return }
        scope.launch {
            val json = try {
                freshSession(minValidityMs = 15 * 60_000L)?.let {
                    JSONObject().put("access_token", it.accessToken).put("user_id", it.userId).put("email", it.email)
                        .put("expires_at", it.expiresAtMs).put("managed", true).toString()
                }
            } catch (e: ApiError) {
                null
            }
            withContext(Dispatchers.Main) { onResult(json) }
        }
    }

    // --- Account actions (UI) --------------------------------------------------------------------

    fun signIn(emailInput: String, password: String, onDone: (Boolean) -> Unit) = authenticate(emailInput, password, create = false, onDone)

    fun signUp(emailInput: String, password: String, onDone: (Boolean) -> Unit) = authenticate(emailInput, password, create = true, onDone)

    private fun authenticate(emailInput: String, password: String, create: Boolean, onDone: (Boolean) -> Unit) {
        val address = emailInput.trim()
        if (address.isEmpty() || password.isEmpty()) {
            message = "이메일과 비밀번호를 입력해 주세요."
            onDone(false)
            return
        }
        busy = true
        message = null
        scope.launch {
            var ok = false
            try {
                val session = if (create) SupabaseApi.signUp(address, password) else SupabaseApi.signIn(address, password)
                if (session == null) {
                    post("가입 완료! 메일로 보낸 인증 링크를 누른 뒤 로그인해 주세요.")
                } else {
                    saveSession(session)
                    ok = true
                    post(null)
                    runSync()
                    GameScores.all().forEach { (game, score) -> pushScore(game, score) }
                }
            } catch (e: ApiError) {
                post(e.userMessage)
            }
            withContext(Dispatchers.Main) { busy = false; onDone(ok) }
        }
    }

    fun sendPasswordReset(emailInput: String) {
        val address = emailInput.trim()
        if (address.isEmpty()) {
            message = "비밀번호를 재설정할 이메일을 먼저 입력해 주세요."
            return
        }
        busy = true
        scope.launch {
            try {
                SupabaseApi.sendPasswordReset(address)
                post("재설정 메일을 보냈습니다. 메일함을 확인해 주세요.")
            } catch (e: ApiError) {
                post(e.userMessage)
            }
            withContext(Dispatchers.Main) { busy = false }
        }
    }

    /** Logs out on this phone. The data stays on the phone and in the account. */
    fun signOut() {
        prefs?.edit()?.clear()?.apply()
        email = null
        lastSyncMs = 0L
        message = "로그아웃했습니다. 이 폰의 데이터는 그대로 남아 있습니다."
    }

    // --- Sync -----------------------------------------------------------------------------------

    /** Manual "sync now" button. */
    fun syncNow() {
        if (!signedIn || busy) return
        busy = true
        message = null
        scope.launch {
            runSync()
            withContext(Dispatchers.Main) { busy = false }
        }
    }

    /** Automatic sync when the app comes to the front or leaves it; skipped when it just ran. */
    fun syncIfSignedIn() {
        if (!signedIn) return
        val now = System.currentTimeMillis()
        if (now - lastAutoSync < MIN_AUTO_SYNC_GAP_MS) return
        lastAutoSync = now
        scope.launch { runSync() }
    }

    private fun syncSoon() {
        scope.launch { runSync() }
    }

    private suspend fun runSync() = lock.withLock {
        val context = appContext ?: return@withLock
        val p = prefs ?: return@withLock
        try {
            val session = freshSession() ?: return@withLock
            val db = BrowserDatabase.get(context)
            val local = readLocal(db)
            val remote = SupabaseApi.fetchSnapshot(session.accessToken)
            val lastFingerprint = p.getString(KEY_FINGERPRINT, null)
            val lastStamp = p.getString(KEY_STAMP, null)

            val stamp: String
            when {
                remote == null -> {
                    stamp = SupabaseApi.pushSnapshot(session.accessToken, session.userId, local)
                }
                remote.updatedAt != lastStamp -> {
                    val cloud = SyncSnapshot.parse(remote.data) ?: SyncSnapshot()
                    val localChanged = lastFingerprint != null && local.fingerprint() != lastFingerprint
                    if (localChanged) {
                        // Both sides changed: this phone's edits win, scores keep the best of both.
                        val merged = local.withBestScores(cloud.gameScores).copy(checklistId = local.checklistId ?: cloud.checklistId)
                        stamp = SupabaseApi.pushSnapshot(session.accessToken, session.userId, merged)
                        applyLocal(db, merged)
                    } else {
                        val merged = cloud.withBestScores(local.gameScores).copy(checklistId = cloud.checklistId ?: local.checklistId)
                        applyLocal(db, merged)
                        if (merged.fingerprint() != cloud.fingerprint()) {
                            // A score on this phone beat the server's: send it up.
                            stamp = SupabaseApi.pushSnapshot(session.accessToken, session.userId, merged)
                        } else {
                            stamp = remote.updatedAt
                        }
                    }
                }
                local.fingerprint() != lastFingerprint -> {
                    stamp = SupabaseApi.pushSnapshot(session.accessToken, session.userId, local)
                }
                else -> stamp = remote.updatedAt
            }
            // Remember what this phone holds after the sync (applying may have rewritten rows).
            val after = readLocal(db).fingerprint()
            val now = System.currentTimeMillis()
            p.edit().putString(KEY_STAMP, stamp).putString(KEY_FINGERPRINT, after).putLong(KEY_LAST_SYNC, now).apply()
            withContext(Dispatchers.Main) { lastSyncMs = now; message = null }
        } catch (e: ApiError) {
            if (e.authExpired) withContext(Dispatchers.Main) { signOut(); message = e.userMessage }
            else post(e.userMessage)
        }
    }

    private suspend fun readLocal(db: BrowserDatabase): SyncSnapshot {
        val dials = SpeedDialTree.flatten(db.speedDialDao().getAll())
        val marks = db.bookmarkDao().getAll().map { SyncSnapshot.Link(it.title, it.url) }
        return withContext(Dispatchers.Main) {
            SyncSnapshot(
                speedDials = dials,
                bookmarks = marks,
                gameScores = GameScores.all(),
                dtmfEnabled = AppSettings.dtmfEnabled,
                dynamicColor = AppSettings.dynamicColor,
                themeMode = AppSettings.themeMode.key,
                accent = AppSettings.accent.key,
                rejectMessages = RejectMessageStore.messages.toList(),
                checklistId = prefs?.getString(KEY_CHECKLIST, null),
            )
        }
    }

    private suspend fun applyLocal(db: BrowserDatabase, snapshot: SyncSnapshot) {
        db.withTransaction {
            db.speedDialDao().deleteAll()
            // Top-level rows first so folders have ids before their children are inserted.
            val planned = SpeedDialTree.plan(snapshot.speedDials)
            val ids = LongArray(planned.size)
            planned.forEachIndexed { index, p ->
                if (p.parentIndex == -1) {
                    ids[index] = db.speedDialDao().insert(
                        SpeedDial(title = p.link.title, url = p.link.url, position = p.position, isFolder = p.link.folder)
                    )
                }
            }
            planned.forEachIndexed { index, p ->
                if (p.parentIndex != -1) {
                    db.speedDialDao().insert(
                        SpeedDial(title = p.link.title, url = p.link.url, position = p.position, parentId = ids[p.parentIndex])
                    )
                }
            }
            db.bookmarkDao().deleteAll()
            val now = System.currentTimeMillis()
            snapshot.bookmarks.forEachIndexed { index, link ->
                // The list is newest first, so earlier entries get later timestamps.
                db.bookmarkDao().upsert(Bookmark(url = link.url, title = link.title, createdAt = now - index))
            }
        }
        withContext(Dispatchers.Main) {
            snapshot.gameScores.forEach { (id, score) -> GameScores.submit(id, score) }
            AppSettings.updateDtmfEnabled(snapshot.dtmfEnabled)
            AppSettings.updateDynamicColor(snapshot.dynamicColor)
            AppSettings.updateThemeMode(ThemeMode.fromKey(snapshot.themeMode))
            AppSettings.updateAccent(AccentColor.fromKey(snapshot.accent))
            if (snapshot.rejectMessages.isNotEmpty()) RejectMessageStore.replaceAll(snapshot.rejectMessages)
        }
        snapshot.checklistId?.let { prefs?.edit()?.putString(KEY_CHECKLIST, it)?.apply() }
    }

    // --- Session --------------------------------------------------------------------------------

    private fun saveSession(session: AuthSession) {
        prefs?.edit()
            ?.putString(KEY_EMAIL, session.email)
            ?.putString(KEY_USER, session.userId)
            ?.putString(KEY_ACCESS, session.accessToken)
            ?.putString(KEY_REFRESH, session.refreshToken)
            ?.putLong(KEY_EXPIRES, session.expiresAtMs)
            ?.apply()
        email = session.email
    }

    /** The stored session, refreshed first when its access token is about to expire. */
    private fun freshSession(minValidityMs: Long = 60_000L): AuthSession? {
        val p = prefs ?: return null
        val refresh = p.getString(KEY_REFRESH, null) ?: return null
        val stored = AuthSession(
            accessToken = p.getString(KEY_ACCESS, "").orEmpty(),
            refreshToken = refresh,
            expiresAtMs = p.getLong(KEY_EXPIRES, 0L),
            userId = p.getString(KEY_USER, "").orEmpty(),
            email = p.getString(KEY_EMAIL, "").orEmpty(),
        )
        if (stored.expiresAtMs - System.currentTimeMillis() > minValidityMs && stored.accessToken.isNotEmpty()) return stored
        val renewed = SupabaseApi.refresh(refresh)
        saveSession(renewed)
        return renewed
    }

    private suspend fun post(text: String?) = withContext(Dispatchers.Main) { message = text }
}
