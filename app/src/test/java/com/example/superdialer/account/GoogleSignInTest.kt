package com.example.superdialer.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleSignInTest {
    @Test fun challengeMatchesTheRfc7636Example() {
        // RFC 7636 appendix B
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            GoogleSignIn.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test fun verifiersAreLongUrlSafeAndDifferent() {
        val a = GoogleSignIn.newVerifier()
        val b = GoogleSignIn.newVerifier()
        assertEquals(64, a.length)
        assertTrue(a.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertFalse(a == b)
    }

    @Test fun aReturnedCodeIsRead() {
        val result = GoogleSignIn.parseCallback("superdialer://auth?code=abc-123_XYZ")
        assertEquals(GoogleSignIn.Callback.Code("abc-123_XYZ"), result)
    }

    @Test fun percentEncodedValuesAreDecoded() {
        val result = GoogleSignIn.parseCallback("superdialer://auth?code=a%2Bb%3Dc")
        assertEquals(GoogleSignIn.Callback.Code("a+b=c"), result)
    }

    @Test fun cancellingIsAFriendlyFailure() {
        val result = GoogleSignIn.parseCallback("superdialer://auth?error=access_denied&error_description=User+denied")
        assertEquals(GoogleSignIn.Callback.Failure("구글 로그인을 취소했어요."), result)
    }

    @Test fun otherErrorsCarryTheServersDescription() {
        val query = GoogleSignIn.parseCallback("superdialer://auth?error=server_error&error_description=Unable+to+exchange+external+code")
        assertEquals(GoogleSignIn.Callback.Failure("Unable to exchange external code"), query)
        // some errors come back in the fragment instead
        val fragment = GoogleSignIn.parseCallback("superdialer://auth#error=server_error&error_description=Bad+things")
        assertEquals(GoogleSignIn.Callback.Failure("Bad things"), fragment)
    }

    @Test fun anEmptyCallbackIsAFailureNotACode() {
        assertTrue(GoogleSignIn.parseCallback("superdialer://auth") is GoogleSignIn.Callback.Failure)
        assertTrue(GoogleSignIn.parseCallback("superdialer://auth?code=") is GoogleSignIn.Callback.Failure)
    }

    @Test fun otherAddressesAreNotOurs() {
        assertEquals(GoogleSignIn.Callback.NotOurs, GoogleSignIn.parseCallback(null))
        assertEquals(GoogleSignIn.Callback.NotOurs, GoogleSignIn.parseCallback("tel:0101234"))
        assertEquals(GoogleSignIn.Callback.NotOurs, GoogleSignIn.parseCallback("https://auth?code=x"))
        assertEquals(GoogleSignIn.Callback.NotOurs, GoogleSignIn.parseCallback("superdialer://other?code=x"))
        assertEquals(GoogleSignIn.Callback.NotOurs, GoogleSignIn.parseCallback("not a uri at all"))
    }

    @Test fun aPendingSignInExpiresAfterTenMinutes() {
        val start = 1_000_000L
        assertTrue(GoogleSignIn.isPendingFresh(start, start + 60_000))
        assertTrue(GoogleSignIn.isPendingFresh(start, start + GoogleSignIn.PENDING_MAX_AGE_MS))
        assertFalse(GoogleSignIn.isPendingFresh(start, start + GoogleSignIn.PENDING_MAX_AGE_MS + 1))
        assertFalse(GoogleSignIn.isPendingFresh(0, 5)) // nothing was started
        assertFalse(GoogleSignIn.isPendingFresh(start, start - 1)) // clock went backwards
    }

    @Test fun authorizeUrlCarriesTheChallengeAndTheRedirect() {
        val url = SupabaseApi.googleAuthorizeUrl("CHALLENGE")
        assertTrue(url.startsWith("https://wugixanaquagedgtqmyv.supabase.co/auth/v1/authorize?provider=google"))
        assertTrue("redirect_to=superdialer%3A%2F%2Fauth" in url)
        assertTrue("code_challenge=CHALLENGE" in url)
        assertTrue("code_challenge_method=s256" in url)
    }

    @Test fun unavailableMessageExplainsWhenTheProviderIsOff() {
        val off = SupabaseApi.googleUnavailableMessage(400, """{"code":400,"error_code":"validation_failed","msg":"Unsupported provider: provider is not enabled"}""")
        assertTrue("아직 켜져 있지 않" in off)
        assertEquals("서버에 문제가 있습니다. 잠시 후 다시 시도해 주세요.", SupabaseApi.googleUnavailableMessage(503, ""))
    }
}
