package com.example.superdialer.messages

import android.provider.Telephony
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRepositoryTest {
    @Test fun inboxIsIncoming() {
        assertFalse(SmsRepository.isOutgoing(Telephony.Sms.MESSAGE_TYPE_INBOX))
    }

    @Test fun sentOutboxQueuedAndFailedAreOutgoing() {
        assertTrue(SmsRepository.isOutgoing(Telephony.Sms.MESSAGE_TYPE_SENT))
        assertTrue(SmsRepository.isOutgoing(Telephony.Sms.MESSAGE_TYPE_OUTBOX))
        assertTrue(SmsRepository.isOutgoing(Telephony.Sms.MESSAGE_TYPE_QUEUED))
        assertTrue(SmsRepository.isOutgoing(Telephony.Sms.MESSAGE_TYPE_FAILED))
    }
}
