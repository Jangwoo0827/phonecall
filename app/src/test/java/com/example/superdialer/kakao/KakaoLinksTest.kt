package com.example.superdialer.kakao

import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoLinksTest {
    private fun action(id: Long, mime: String, label: String) = KakaoAction(id, mime, KakaoLinks.kindOf(mime, label), label)

    @Test fun kindIsReadFromTheMediaTypeOrTheLabel() {
        assertEquals(KakaoKind.Chat, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk.chat", ""))
        assertEquals(KakaoKind.Voice, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk.voice", ""))
        assertEquals(KakaoKind.Video, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk.video", ""))
        // when the media type says nothing, the label does
        assertEquals(KakaoKind.Voice, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk", "보이스톡"))
        assertEquals(KakaoKind.Video, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk", "페이스톡"))
        assertEquals(KakaoKind.Chat, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk", "카카오톡 채팅"))
        assertEquals(KakaoKind.Other, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk", "프로필"))
    }

    @Test fun aVoiceCallIsNotMistakenForAChatBecauseOfAWordInTheType() {
        // "voip" / "voice" win over "chat" / "message" when both appear
        assertEquals(KakaoKind.Voice, KakaoLinks.kindOf("vnd.android.cursor.item/vnd.com.kakao.talk.voice.message", ""))
    }

    @Test fun oneEntryPerKindInTheOrderChatVoiceVideoOther() {
        val rows = listOf(
            action(1, "vnd.android.cursor.item/vnd.com.kakao.talk.video", "페이스톡"),
            action(2, "vnd.android.cursor.item/vnd.com.kakao.talk.chat", "카카오톡 채팅"),
            action(3, "vnd.android.cursor.item/vnd.com.kakao.talk.chat", "카카오톡 채팅"), // duplicate kind
            action(4, "vnd.android.cursor.item/vnd.com.kakao.talk.voice", "보이스톡"),
        )
        assertEquals(listOf(2L, 4L, 1L), KakaoLinks.arrange(rows).map { it.dataId })
    }

    @Test fun buttonTextIsOurNameForKnownKindsAndTheEntryLabelOtherwise() {
        assertEquals("보이스톡", KakaoLinks.buttonLabel(action(1, "x/kakao.voice", "Voice call")))
        assertEquals("프로필 보기", KakaoLinks.buttonLabel(action(2, "x/kakao.profile", "프로필 보기")))
        assertEquals("카카오톡", KakaoLinks.buttonLabel(action(3, "x/kakao.profile", "")))
    }
}
