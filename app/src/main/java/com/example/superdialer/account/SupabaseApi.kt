package com.example.superdialer.account

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
        return parseSession(body, System.currentTimeMillis()) ?: throw ApiError("로그인 응답을 읽을 수 없습니다.")
    }

    fun refresh(refreshToken: String): AuthSession {
        val (code, body) = request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken))
        if (code in 400..401) throw ApiError("로그인이 만료되었습니다. 다시 로그인해 주세요.", authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseSession(body, System.currentTimeMillis()) ?: throw ApiError("로그인 응답을 읽을 수 없습니다.", authExpired = true)
    }

    fun sendPasswordReset(email: String) {
        val (code, body) = request("POST", "/auth/v1/recover", JSONObject().put("email", email))
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
    }

    fun fetchSnapshot(accessToken: String): RemoteSnapshot? {
        val (code, body) = request("GET", "/rest/v1/superdialer_sync?select=data,updated_at&limit=1", null, accessToken)
        if (code == 401) throw ApiError("로그인이 만료되었습니다. 다시 로그인해 주세요.", authExpired = true)
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
        if (code == 401) throw ApiError("로그인이 만료되었습니다. 다시 로그인해 주세요.", authExpired = true)
        if (code !in 200..299) throw ApiError(errorMessage(code, body))
        return parseRemote(body)?.updatedAt ?: throw ApiError("저장 응답을 읽을 수 없습니다.")
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
            throw ApiError("서버에 연결할 수 없습니다.")
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
            throw ApiError("네트워크에 연결할 수 없습니다.")
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
            "invalid login" in text -> "이메일 또는 비밀번호가 맞지 않습니다."
            "not confirmed" in text -> "메일로 보낸 인증 링크를 먼저 눌러 주세요."
            "already registered" in text || "already been registered" in text -> "이미 가입된 이메일입니다. 로그인해 주세요."
            "password" in text && ("at least" in text || "weak" in text || "short" in text) -> "비밀번호는 6자 이상이어야 합니다."
            "valid email" in text || "invalid email" in text || "email address" in text && "invalid" in text -> "올바른 이메일 주소를 입력해 주세요."
            "rate limit" in text || code == 429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            code in 500..599 -> "서버에 문제가 있습니다. 잠시 후 다시 시도해 주세요."
            raw.isNotEmpty() -> raw
            else -> "요청에 실패했습니다 ($code)."
        }
    }
}
