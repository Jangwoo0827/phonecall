package com.example.superdialer.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsRepositoryTest {
    @Test fun mmsDatesAreSecondsNotMillis() {
        assertEquals(1_791_469_000_000L, MmsRepository.mmsDateMillis(1_791_469_000L))
    }

    @Test fun onlyTheInboxIsIncoming() {
        assertFalse(MmsRepository.isOutgoing(1))
        assertTrue(MmsRepository.isOutgoing(2)) // sent
        assertTrue(MmsRepository.isOutgoing(4)) // outbox
        assertTrue(MmsRepository.isOutgoing(5)) // failed
    }

    @Test fun textAndLayoutPartsAreNotAttachments() {
        assertFalse(MmsRepository.isAttachment("text/plain"))
        assertFalse(MmsRepository.isAttachment("application/smil"))
        assertFalse(MmsRepository.isAttachment(""))
        assertTrue(MmsRepository.isAttachment("image/jpeg"))
        assertTrue(MmsRepository.isAttachment("video/3gpp"))
        assertTrue(MmsRepository.isAttachment("text/x-vCard"))
    }

    @Test fun recipientIdsAreParsedFromTheThreadRow() {
        assertEquals(listOf(12L, 34L), MmsRepository.recipientIds("12 34"))
        assertEquals(listOf(7L), MmsRepository.recipientIds("7"))
        assertEquals(emptyList<Long>(), MmsRepository.recipientIds(""))
        assertEquals(listOf(1L, 2L), MmsRepository.recipientIds("1, 2 x"))
    }

    @Test fun threadsAreMatchedThroughTheirRecipients() {
        val threads = listOf(1L to "5", 2L to "6 7", 3L to "8", 4L to "")
        assertEquals(setOf(1L, 2L), MmsRepository.threadsMatching(threads, setOf(5L, 7L)))
        assertEquals(emptySet<Long>(), MmsRepository.threadsMatching(threads, setOf(99L)))
    }

    @Test fun attachmentLabelsAndImageDetection() {
        assertTrue(MmsAttachment(1, "image/png", "a.png").isImage)
        assertFalse(MmsAttachment(1, "video/mp4", "").isImage)
        assertEquals("동영상", MmsAttachment(1, "video/mp4", "x.mp4").label)
        assertEquals("음성", MmsAttachment(1, "audio/amr", "").label)
        assertEquals("연락처 카드", MmsAttachment(1, "text/x-vCard", "").label)
        assertEquals("메모.pdf", MmsAttachment(1, "application/pdf", "메모.pdf").label)
        assertEquals("첨부 파일", MmsAttachment(1, "application/octet-stream", "").label)
    }

    @Test fun textAndMultimediaMessagesMergeNewestFirst() {
        val sms = listOf(SmsMessage(1, "010", "old text", 1_000, false))
        val mms = listOf(SmsMessage(2, "010", "", 5_000, true, isMms = true), SmsMessage(3, "010", "mid", 3_000, false, isMms = true))
        val merged = SmsRepository.newestFirst(sms + mms, 2)
        assertEquals(listOf(2L, 3L), merged.map { it.id })
    }

    @Test fun pictureDownscaleKeepsTheLongSideNearTheTarget() {
        assertEquals(1, sampleSize(640, 480, 720))
        assertEquals(1, sampleSize(1000, 700, 720)) // halving would drop below the target
        assertEquals(2, sampleSize(1600, 1200, 720))
        assertEquals(4, sampleSize(3000, 4000, 720))
        assertEquals(1, sampleSize(0, 0, 720)) // unknown size: no downscale
    }
}
