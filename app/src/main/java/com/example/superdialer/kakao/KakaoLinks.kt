package com.example.superdialer.kakao

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast

/** What a KakaoTalk entry of a contact does. */
enum class KakaoKind(val label: String) {
    Chat("카카오톡 채팅"), Voice("보이스톡"), Video("페이스톡"), Other("카카오톡")
}

/** One KakaoTalk entry in the phone's contacts (the buttons the phone's own contact screen shows): chat, voice or video call. */
data class KakaoAction(val dataId: Long, val mimeType: String, val kind: KakaoKind, val label: String)

/**
 * KakaoTalk has no public API for other apps, but it does register entries in the phone's contacts (one row per way
 * to reach that person: chat, 보이스톡, 페이스톡). A contacts app opens such a row with `ACTION_VIEW` on the row's
 * address and its media type, and KakaoTalk answers; that is all this does, so nothing is read from KakaoTalk itself.
 */
object KakaoLinks {
    const val PACKAGE = "com.kakao.talk"

    /**
     * The KakaoTalk entries of one contact, in the order chat, 보이스톡, 페이스톡, other (one per kind). Empty when the
     * contact is not a KakaoTalk friend or contacts cannot be read. Blocking I/O.
     */
    fun actionsFor(context: Context, contactId: Long): List<KakaoAction> = try {
        val found = ArrayList<KakaoAction>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} LIKE ?",
            arrayOf(contactId.toString(), "%kakao%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(1).orEmpty()
                val label = c.getString(2).orEmpty()
                found += KakaoAction(c.getLong(0), mime, kindOf(mime, label), label)
            }
        }
        arrange(found)
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** Opens that entry the way the contact screen does; falls back to just opening KakaoTalk. */
    fun open(context: Context, action: KakaoAction) {
        val uri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, action.mimeType)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            openApp(context)
        } catch (e: SecurityException) {
            openApp(context)
        }
    }

    /** The share intent that goes to KakaoTalk only; the person to send to is picked inside KakaoTalk. */
    fun shareIntent(text: String): Intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
        .setPackage(PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands [text] (a link, a number...) to KakaoTalk's own "send to" screen. False when KakaoTalk is not installed. */
    fun share(context: Context, text: String): Boolean = try {
        context.startActivity(shareIntent(text))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "카카오톡이 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
        false
    }

    /** Opens KakaoTalk itself. */
    fun openApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        if (launch == null) {
            Toast.makeText(context, "카카오톡을 열 수 없습니다. 설치되어 있나요?", Toast.LENGTH_SHORT).show()
            return
        }
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** Chat, voice call or video call, from the entry's media type and its label (whichever says it). */
    fun kindOf(mimeType: String, label: String): KakaoKind {
        val text = (mimeType + " " + label).lowercase()
        return when {
            "voice" in text || "voip" in text || "보이스" in text -> KakaoKind.Voice
            "face" in text || "video" in text || "페이스" in text || "영상" in text -> KakaoKind.Video
            "chat" in text || "채팅" in text || "message" in text || "msg" in text -> KakaoKind.Chat
            else -> KakaoKind.Other
        }
    }

    /** One entry per kind (the first), chat first, then 보이스톡, 페이스톡, other. */
    fun arrange(actions: List<KakaoAction>): List<KakaoAction> =
        actions.distinctBy { it.kind }.sortedBy { it.kind.ordinal }

    /** The button text: our own name for the three known kinds, the entry's own label for anything else. */
    fun buttonLabel(action: KakaoAction): String =
        if (action.kind == KakaoKind.Other) action.label.ifBlank { KakaoKind.Other.label } else action.kind.label
}
