package com.example.superdialer.calllog

import android.provider.CallLog

enum class CallType(val label: String) {
    Incoming("수신"),
    Outgoing("발신"),
    Missed("부재중"),
    Rejected("거절"),
    Blocked("차단"),
    Voicemail("음성메일"),
    Other("통화");

    companion object {
        fun fromCallLog(type: Int): CallType = when (type) {
            CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> Incoming
            CallLog.Calls.OUTGOING_TYPE -> Outgoing
            CallLog.Calls.MISSED_TYPE -> Missed
            CallLog.Calls.REJECTED_TYPE -> Rejected
            CallLog.Calls.BLOCKED_TYPE -> Blocked
            CallLog.Calls.VOICEMAIL_TYPE -> Voicemail
            else -> Other
        }
    }
}

data class CallLogEntry(
    val id: Long,
    /** Raw number as stored; empty for private/unknown callers. */
    val number: String,
    /** Contact name, if the number is saved in contacts. */
    val name: String?,
    /** Contact id, if the number is saved in contacts. */
    val contactId: Long?,
    val type: CallType,
    val dateMillis: Long,
    val durationSeconds: Long,
)

/** Same key for "+82 10-1234-5678", "010-1234-5678" and "01012345678". */
fun numberKey(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return if (raw.trim().startsWith("+82")) "0" + digits.removePrefix("82") else digits
}
