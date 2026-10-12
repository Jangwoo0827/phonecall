package com.example.superdialer.account

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Google sign-in through Supabase's browser flow with PKCE (RFC 7636), no Google SDK: the app opens
 * `/auth/v1/authorize?provider=google` in the browser, Google and Supabase do the login, and the browser comes back to
 * [REDIRECT_URI] with a one-time `code` that only this app can exchange, because only it knows the PKCE verifier.
 *
 * The pieces here are pure so they can be unit tested; the app-side steps are in [AccountManager].
 */
object GoogleSignIn {
    const val REDIRECT_SCHEME = "superdialer"
    const val REDIRECT_HOST = "auth"
    const val REDIRECT_URI = "$REDIRECT_SCHEME://$REDIRECT_HOST"

    /** A started sign-in is only valid for this long; an old callback is ignored. */
    const val PENDING_MAX_AGE_MS = 10 * 60_000L

    /** 64 random URL-safe characters (RFC 7636 asks for 43 to 128). */
    fun newVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        return base64Url(bytes)
    }

    /** The S256 challenge sent to the server: base64url(SHA-256(verifier)). */
    fun challenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun isPendingFresh(startedAtMs: Long, nowMs: Long): Boolean =
        startedAtMs > 0 && nowMs - startedAtMs in 0..PENDING_MAX_AGE_MS

    sealed interface Callback {
        /** The login worked: this code is exchanged for a session. */
        data class Code(val code: String) : Callback

        /** The login was cancelled or refused. */
        data class Failure(val message: String) : Callback

        /** Not a sign-in redirect at all. */
        data object NotOurs : Callback
    }

    /** Reads the address the browser returned to; the error can arrive in the query or in the fragment. */
    fun parseCallback(url: String?): Callback {
        val uri = try {
            URI(url ?: return Callback.NotOurs)
        } catch (e: java.net.URISyntaxException) {
            return Callback.NotOurs
        }
        if (uri.scheme != REDIRECT_SCHEME || uri.host != REDIRECT_HOST) return Callback.NotOurs

        val params = HashMap<String, String>()
        listOf(uri.rawFragment, uri.rawQuery).forEach { raw ->
            raw?.split('&')?.forEach { pair ->
                val key = pair.substringBefore('=')
                if (key.isNotEmpty() && '=' in pair) params[decode(key)] = decode(pair.substringAfter('='))
            }
        }
        params["code"]?.takeIf { it.isNotBlank() }?.let { return Callback.Code(it) }

        val error = params["error"].orEmpty()
        val description = params["error_description"].orEmpty()
        return Callback.Failure(
            when {
                error == "access_denied" -> "구글 로그인을 취소했어요."
                description.isNotBlank() -> description
                error.isNotBlank() -> "구글 로그인에 실패했어요 ($error)."
                else -> "구글 로그인 응답을 읽을 수 없어요."
            }
        )
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        value
    }
}
