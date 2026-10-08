package com.example.superdialer.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseApiTest {
    @Test fun parsesASessionAndComputesExpiry() {
        val body = """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u-1","email":"me@x.com"}}"""
        val s = SupabaseApi.parseSession(body, 1_000L)!!
        assertEquals("a", s.accessToken)
        assertEquals("r", s.refreshToken)
        assertEquals("u-1", s.userId)
        assertEquals("me@x.com", s.email)
        assertEquals(1_000L + 3_600_000L, s.expiresAtMs)
    }

    @Test fun signUpWithoutTokensMeansEmailConfirmationIsPending() {
        assertNull(SupabaseApi.parseSession("""{"id":"u-1","email":"me@x.com","confirmation_sent_at":"now"}""", 0))
        assertNull(SupabaseApi.parseSession("garbage", 0))
    }

    @Test fun parsesTheStoredRow() {
        val remote = SupabaseApi.parseRemote("""[{"data":{"v":1,"dtmfEnabled":false},"updated_at":"2026-10-08T01:02:03+00:00"}]""")!!
        assertEquals("2026-10-08T01:02:03+00:00", remote.updatedAt)
        assertNotNull(SyncSnapshot.parse(remote.data))
        assertNull(SupabaseApi.parseRemote("[]"))
    }

    @Test fun errorsBecomeKoreanMessages() {
        assertEquals("이메일 또는 비밀번호가 맞지 않습니다.", SupabaseApi.errorMessage(400, """{"error_description":"Invalid login credentials"}"""))
        assertEquals("메일로 보낸 인증 링크를 먼저 눌러 주세요.", SupabaseApi.errorMessage(400, """{"msg":"Email not confirmed"}"""))
        assertEquals("이미 가입된 이메일입니다. 로그인해 주세요.", SupabaseApi.errorMessage(422, """{"msg":"User already registered"}"""))
        assertEquals("비밀번호는 6자 이상이어야 합니다.", SupabaseApi.errorMessage(422, """{"msg":"Password should be at least 6 characters."}"""))
        assertEquals("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", SupabaseApi.errorMessage(429, "{}"))
        assertEquals("요청에 실패했습니다 (418).", SupabaseApi.errorMessage(418, ""))
    }

    @Test fun parsesLeaderboardRows() {
        val rows = SupabaseApi.parseLeaderboard("""[{"user_id":"u1","nickname":"A","score":50},{"user_id":"u2","nickname":"","score":9},{"user_id":"u3","nickname":"B","score":7}]""")
        assertEquals(listOf(LeaderRow("u1", "A", 50), LeaderRow("u3", "B", 7)), rows)
        assertEquals(emptyList<LeaderRow>(), SupabaseApi.parseLeaderboard("nope"))
    }
}
