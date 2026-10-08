package com.example.superdialer.contacts

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.example.superdialer.calllog.numberKey
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** An app-side contact group (a named list of contact ids). The system contacts database is not touched. */
data class ContactGroup(val id: String, val name: String, val members: Set<Long>)

/**
 * Contact groups kept in this app (SharedPreferences). They hold contact ids of this phone, so they are not synced
 * to the account. Call [init] once from the Application.
 */
object ContactGroups {
    private const val PREFS = "contact_groups"
    private const val KEY = "groups"

    private var appContext: Context? = null

    /** Observable by Compose. */
    val groups = mutableStateListOf<ContactGroup>()

    fun init(context: Context) {
        appContext = context.applicationContext
        groups.clear()
        groups.addAll(decode(appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)))
    }

    fun create(name: String): ContactGroup? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val group = ContactGroup(UUID.randomUUID().toString(), clean, emptySet())
        groups.add(group)
        save()
        return group
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        groups.removeAll { it.id == id }
        save()
    }

    fun setMember(id: String, contactId: Long, member: Boolean) =
        update(id) { it.copy(members = if (member) it.members + contactId else it.members - contactId) }

    fun get(id: String?): ContactGroup? = groups.firstOrNull { it.id == id }

    /** Contacts that were merged or deleted get a new/no id; drop ids that no longer exist. */
    fun prune(existingContactIds: Set<Long>) {
        var changed = false
        for (i in groups.indices) {
            val kept = groups[i].members.filter { it in existingContactIds }.toSet()
            if (kept.size != groups[i].members.size) {
                groups[i] = groups[i].copy(members = kept)
                changed = true
            }
        }
        if (changed) save()
    }

    private fun update(id: String, change: (ContactGroup) -> ContactGroup) {
        val index = groups.indexOfFirst { it.id == id }
        if (index < 0) return
        groups[index] = change(groups[index])
        save()
    }

    private fun save() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(groups))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    fun encode(list: List<ContactGroup>): String = JSONArray().also { array ->
        list.forEach { g ->
            array.put(JSONObject().put("id", g.id).put("name", g.name).put("members", JSONArray(g.members.toList())))
        }
    }.toString()

    fun decode(text: String?): List<ContactGroup> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) return@mapNotNull null
                val members = o.optJSONArray("members")
                ContactGroup(id, name, (0 until (members?.length() ?: 0)).map { members!!.getLong(it) }.toSet())
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

/** Why a set of contacts look like the same person. */
enum class DuplicateReason(val label: String) { SameNumber("같은 번호"), SameName("같은 이름") }

data class DuplicateGroup(val contacts: List<Contact>, val reason: DuplicateReason)

/** Contacts that share a phone number or have the same name (ignoring case and spaces). Pure, unit tested. */
fun findDuplicates(contacts: List<Contact>): List<DuplicateGroup> {
    val parent = IntArray(contacts.size) { it }
    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) r = parent[r]
        var c = x
        while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
        return r
    }
    fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

    val byNumber = HashMap<String, Int>()
    val byName = HashMap<String, Int>()
    val sharedNumberRoots = HashSet<Int>()
    contacts.forEachIndexed { index, contact ->
        contact.numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet().forEach { key ->
            val other = byNumber.putIfAbsent(key, index)
            if (other != null && other != index) union(index, other)
        }
        val nameKey = contact.name.filterNot { it.isWhitespace() }.lowercase()
        if (nameKey.isNotEmpty()) {
            val other = byName.putIfAbsent(nameKey, index)
            if (other != null) union(index, other)
        }
    }
    // A component counts as "same number" when any number is shared inside it, otherwise it is a same-name match.
    val numberOwners = HashMap<String, MutableList<Int>>()
    contacts.forEachIndexed { index, contact ->
        contact.numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet().forEach { numberOwners.getOrPut(it) { mutableListOf() } += index }
    }
    numberOwners.values.filter { it.size > 1 }.forEach { sharedNumberRoots += find(it.first()) }

    return contacts.indices.groupBy { find(it) }.values
        .filter { it.size > 1 }
        .map { members ->
            val root = find(members.first())
            DuplicateGroup(
                contacts = members.map { contacts[it] },
                reason = if (root in sharedNumberRoots) DuplicateReason.SameNumber else DuplicateReason.SameName,
            )
        }
}
