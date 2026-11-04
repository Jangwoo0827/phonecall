package com.example.superdialer.account

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A failure with a message that can be shown to the user as is. */
class ApiError(val userMessage: String, val authExpired: Boolean = false) : Exception(userMessage)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
    val userId: String,
    val email: String,
)

data class RemoteSnapshot(val data: String, val updatedAt: String)

/** One line of a game's leaderboard. */
data class LeaderRow(val userId: String, val nickname: String, val score: Int)

/**
 * Minimal Supabase client (auth + one table) over plain HTTPS, no SDK. The URL and the publishable key are
 * public by design (they only identify the project); what a signed-in user can reach is limited to their own
 * row by row-level security on `superdialer_sync`.
 */
object SupabaseApi {
    private const val URL = "https://wugixanaquagedgtqmyv.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_6uOB_4z0trMhE-cJKLArEw_F_wwTZWf"
    private const val TIMEOUT_MS = 15_000

    /** Blocking. Returns null when the project asks for the email to be confirmed before the first login. */
    fun signUp(email: String, password: String): AuthSession? {
        val (code, body) = request("POST", "/auth/v1/signup", JSONObject().put("email", email).put("password", password))
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseSession(body, System.currentTimeMillis())
    }

    fun signIn(email: String, password: String): AuthSession {
        val (code, body) = request(
            "POST", "/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password),
        )
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseSession(body, System.currentTimeMillis()) ?: throw ApiError(str(R.string.t_can_t_read_the_sign_in))
    }

    /** The browser address that starts Google sign-in; the server sends the login back to [GoogleSignIn.REDIRECT_URI]. */
    fun googleAuthorizeUrl(challenge: String): String =
        "$URL/auth/v1/authorize?provider=google" +
            "&redirect_to=${java.net.URLEncoder.encode(GoogleSignIn.REDIRECT_URI, "UTF-8")}" +
            "&code_challenge=$challenge&code_challenge_method=s256"

    /**
     * Null when Google sign-in is switched on for the project (the authorize address answers with a redirect to
     * Google); otherwise a message to show. Blocking.
     */
    fun googleUnavailable(challenge: String): String? {
        val connection = try {
            URL(googleAuthorizeUrl(challenge)).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return str(R.string.t_can_t_connect_to_the_server)
        }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            val code = connection.responseCode
            if (code in 300..399) {
                null
            } else {
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                googleUnavailableMessage(code, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
            }
        } catch (e: IOException) {
            str(R.string.t_can_t_connect_to_the_network)
        } finally {
            connection.disconnect()
        }
    }

    /** Trades the one-time code from the Google sign-in redirect for a session (PKCE: needs the original verifier). */
    fun exchangeCode(authCode: String, verifier: String): AuthSession {
        val (code, body) = request(
            "POST", "/auth/v1/token?grant_type=pkce",
            JSONObject().put("auth_code", authCode).put("code_verifier", verifier),
        )
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseSession(body, System.currentTimeMillis()) ?: throw ApiError(str(R.string.t_can_t_read_the_sign_in))
    }

    fun refresh(refreshToken: String): AuthSession {
        val (code, body) = request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken))
        if (code in 400..401) throw ApiError(str(R.string.t_your_sign_in_expired_please_sign), authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseSession(body, System.currentTimeMillis()) ?: throw ApiError(str(R.string.t_can_t_read_the_sign_in), authExpired = true)
    }

    /**
     * Deletes this account and everything stored for it on the server (the `superdialer_delete_account` function,
     * see supabase/delete_account.sql). The data on this phone is not touched.
     */
    fun deleteAccount(accessToken: String) {
        val (code, body) = request("POST", "/rest/v1/rpc/superdialer_delete_account", JSONObject(), accessToken)
        if (code == 401) throw ApiError(str(R.string.t_your_sign_in_expired_sign_in), authExpired = true)
        if (code !in 200..299) throw ApiError(deleteAccountErrorMessage(code, body))
    }

    fun sendPasswordReset(email: String) {
        val (code, body) = request("POST", "/auth/v1/recover", JSONObject().put("email", email))
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
    }

    fun fetchSnapshot(accessToken: String): RemoteSnapshot? {
        val (code, body) = request("GET", "/rest/v1/superdialer_sync?select=data,updated_at&limit=1", null, accessToken)
        if (code == 401) throw ApiError(str(R.string.t_your_sign_in_expired_please_sign), authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseRemote(body)
    }

    /** Creates or replaces this user's row; returns the server's `updated_at`. */
    fun pushSnapshot(accessToken: String, userId: String, snapshot: SyncSnapshot): String {
        val row = JSONObject().put("user_id", userId).put("data", snapshot.toJson())
            .put("updated_at", java.time.Instant.now().toString())
        val (code, body) = request(
            "POST", "/rest/v1/superdialer_sync?on_conflict=user_id", JSONArray().put(row), accessToken,
            mapOf("Prefer" to "resolution=merge-duplicates,return=representation"),
        )
        if (code == 401) throw ApiError(str(R.string.t_your_sign_in_expired_please_sign), authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseRemote(body)?.updatedAt ?: throw ApiError(str(R.string.t_can_t_read_the_save_response))
    }

    /** Sends a score; the server keeps the better of this and the stored one. */
    fun submitScore(accessToken: String, gameId: String, score: Int, nickname: String) {
        val body = JSONObject().put("p_game", gameId).put("p_score", score).put("p_nickname", nickname)
        val (code, text) = request("POST", "/rest/v1/rpc/superdialer_submit_score", body, accessToken)
        if (code == 401) throw ApiError(str(R.string.t_your_sign_in_expired_please_sign), authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, text))
    }

    fun fetchLeaderboard(accessToken: String, gameId: String, limit: Int = 20): List<LeaderRow> {
        val path = "/rest/v1/superdialer_scores?game_id=eq.${java.net.URLEncoder.encode(gameId, "UTF-8")}" +
            "&select=user_id,nickname,score&order=score.desc&limit=$limit"
        val (code, text) = request("GET", path, null, accessToken)
        if (code == 401) throw ApiError(str(R.string.t_your_sign_in_expired_please_sign), authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, text))
        return parseLeaderboard(text)
    }

    private fun request(
        method: String,
        path: String,
        json: Any?,
        accessToken: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val connection = try {
            URL(URL + path).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw ApiError(str(R.string.t_can_t_connect_to_the_server))
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: PUBLISHABLE_KEY}")
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (json != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return code to body
        } catch (e: IOException) {
            throw ApiError(str(R.string.t_can_t_connect_to_the_network))
        } finally {
            connection.disconnect()
        }
    }

    // --- Pure helpers (unit tested) ---------------------------------------------------------------

    /** A session from a GoTrue response, or null when the response has no tokens (email confirmation pending). */
    fun parseSession(body: String, nowMs: Long): AuthSession? = try {
        val o = JSONObject(body)
        val access = o.optString("access_token")
        val refresh = o.optString("refresh_token")
        val user = o.optJSONObject("user")
        if (access.isEmpty() || refresh.isEmpty() || user == null) {
            null
        } else {
            AuthSession(
                accessToken = access,
                refreshToken = refresh,
                expiresAtMs = nowMs + o.optLong("expires_in", 3600L) * 1000L,
                userId = user.getString("id"),
                email = user.optString("email"),
            )
        }
    } catch (e: JSONException) {
        null
    }

    fun parseRemote(body: String): RemoteSnapshot? = try {
        val array = JSONArray(body)
        val row = array.optJSONObject(0)
        row?.let { RemoteSnapshot(it.optJSONObject("data")?.toString() ?: "{}", it.getString("updated_at")) }
    } catch (e: JSONException) {
        null
    }

    fun parseLeaderboard(body: String): List<LeaderRow> = try {
        val array = JSONArray(body)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val nickname = o.optString("nickname")
            if (nickname.isBlank()) null else LeaderRow(o.optString("user_id"), nickname, o.optInt("score"))
        }
    } catch (e: JSONException) {
        emptyList()
    }

    /** The function is missing on a server where the SQL file was not run yet; say so instead of a raw error. */
    fun deleteAccountErrorMessage(code: Int, body: String): String =
        if (code == 404 || "could not find the function" in body.lowercase()) {
            str(R.string.t_account_deletion_isn_t_installed_on)
        } else {
            errorMessage(code, body)
        }

    /** Why the authorize address did not redirect: usually the Google provider is not enabled in the Supabase project. */
    fun googleUnavailableMessage(code: Int, body: String): String {
        if ("not enabled" in body.lowercase()) {
            return str(R.string.t_google_sign_in_isn_t_turned)
        }
        return errorMessage(code, body)
    }

    /** Turns the server's error payload into a short Korean message. */
    fun errorMessage(code: Int, body: String): String {
        val raw = try {
            val o = JSONObject(body)
            listOf("error_description", "msg", "message", "error").firstNotNullOfOrNull { o.optString(it).takeIf(String::isNotBlank) }
        } catch (e: JSONException) {
            null
        }.orEmpty()
        val text = raw.lowercase()
        return when {
            "invalid login" in text -> str(R.string.t_the_email_or_password_is_incorrect)
            "not confirmed" in text -> str(R.string.t_tap_the_verification_link_we_emailed)
            "already registered" in text || "already been registered" in text -> str(R.string.t_this_email_is_already_registered_pleas)
            "password" in text && ("at least" in text || "weak" in text || "short" in text) -> str(R.string.t_the_password_must_be_at_least)
            "valid email" in text || "invalid email" in text || "email address" in text && "invalid" in text -> str(R.string.t_enter_a_valid_email_address)
            "rate limit" in text || code == 429 -> str(R.string.t_too_many_requests_please_try_again)
            code in 500..599 -> str(R.string.t_the_server_has_a_problem_please)
            raw.isNotEmpty() -> raw
            else -> str(R.string.t_the_request_failed, code)
        }
    }
}
