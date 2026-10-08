package com.example.superdialer.messages

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.compose.runtime.mutableStateListOf
import com.example.superdialer.calllog.numberKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A text message sent from this app. Apps that are not the default SMS app cannot write to the system inbox, so these are kept here. */
data class SentMessage(
    val id: Long,
    val numberKey: String,
    val address: String,
    val body: String,
    val status: Int,
) {
    companion object {
        const val SENDING = 0
        const val SENT = 1
        const val FAILED = 2
    }
}

/** Messages sent through [SmsSender]. This phone only, never synced. Call [init] once from the Application. */
object SentSms {
    private const val PREFS = "sent_sms"
    private const val KEY = "messages"
    private const val MAX = 300

    private var appContext: Context? = null

    /** Observable by Compose. */
    val messages = mutableStateListOf<SentMessage>()

    fun init(context: Context) {
        appContext = context.applicationContext
        messages.clear()
        messages.addAll(decode(appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)))
    }

    fun add(message: SentMessage) {
        messages.add(message)
        while (messages.size > MAX) messages.removeAt(0)
        save()
    }

    /** [ok] false marks it failed for good; true marks it sent unless a part of it already failed. */
    fun mark(id: Long, ok: Boolean) {
        val index = messages.indexOfFirst { it.id == id }
        if (index < 0) return
        val current = messages[index]
        val next = when {
            !ok -> SentMessage.FAILED
            current.status == SentMessage.FAILED -> SentMessage.FAILED
            else -> SentMessage.SENT
        }
        if (next != current.status) {
            messages[index] = current.copy(status = next)
            save()
        }
    }

    fun remove(id: Long) {
        messages.removeAll { it.id == id }
        save()
    }

    fun forNumbers(numbers: Collection<String>): List<SentMessage> {
        val keys = numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet()
        return messages.filter { it.numberKey in keys }
    }

    private fun save() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(messages))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    fun encode(list: List<SentMessage>): String = JSONArray().also { array ->
        list.forEach {
            array.put(JSONObject().put("i", it.id).put("k", it.numberKey).put("a", it.address).put("b", it.body).put("s", it.status))
        }
    }.toString()

    fun decode(text: String?): List<SentMessage> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val body = o.optString("b")
                if (body.isEmpty()) null else SentMessage(o.optLong("i"), o.optString("k"), o.optString("a"), body, o.optInt("s", SentMessage.SENT))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** System messages plus the ones sent from here, newest first. */
    fun merge(system: List<SmsMessage>, sent: List<SentMessage>): List<SmsMessage> =
        (system + sent.map {
            SmsMessage(id = -it.id, address = it.address, body = it.body, dateMillis = it.id, outgoing = true, status = it.status, sentId = it.id)
        }).sortedByDescending { it.dateMillis }
}

/** Sends texts with [SmsManager]; needs the SEND_SMS permission (checked by the caller). */
object SmsSender {
    const val ACTION_SENT = "com.example.superdialer.SMS_SENT"
    const val EXTRA_ID = "message_id"

    /** Sends [text] to [number] and records it. Returns false when nothing could be started. */
    fun send(context: Context, number: String, text: String, retryId: Long? = null): Boolean {
        val body = text.trim()
        if (body.isEmpty() || number.isBlank()) return false
        val id = System.currentTimeMillis()
        if (retryId != null) SentSms.remove(retryId)
        SentSms.add(SentMessage(id, numberKey(number), number, body, SentMessage.SENDING))
        return try {
            val manager = context.getSystemService(SmsManager::class.java)
            val parts = manager.divideMessage(body)
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { part ->
                val intent = Intent(ACTION_SENT).setPackage(context.packageName).putExtra(EXTRA_ID, id)
                sentIntents += PendingIntent.getBroadcast(
                    context, (id % 100_000).toInt() * 10 + part, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            manager.sendMultipartTextMessage(number, null, parts, sentIntents, null)
            true
        } catch (e: RuntimeException) {
            SentSms.mark(id, ok = false)
            false
        }
    }
}

/** Receives the result of each sent message part and updates its status. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(SmsSender.EXTRA_ID, 0L)
        if (id != 0L) SentSms.mark(id, ok = resultCode == Activity.RESULT_OK)
    }
}
