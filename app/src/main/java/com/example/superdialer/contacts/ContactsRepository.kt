package com.example.superdialer.contacts

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone

class ContactsRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** All contacts that have a phone number, sorted 가나다순. Blocking I/O. */
    fun loadAll(): List<Contact> {
        val numbersByContact = HashMap<Long, MutableList<String>>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1) ?: continue
                numbersByContact.getOrPut(c.getLong(0)) { mutableListOf() } += number
            }
        }

        val contacts = ArrayList<Contact>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.STARRED,
            ),
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER}=1",
            null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val numbers = numbersByContact[id] ?: continue
                contacts += Contact(
                    id = id,
                    name = c.getString(1).orEmpty(),
                    starred = c.getInt(2) == 1,
                    numbers = numbers,
                )
            }
        }
        return contacts.sortedWith(ContactSorting.comparator)
    }

    fun loadDetail(contactId: Long): ContactDetail? {
        var name: String? = null
        var starred = false
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.STARRED),
            "${ContactsContract.Contacts._ID}=?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0).orEmpty()
                starred = c.getInt(1) == 1
            }
        }
        val displayName = name ?: return null

        val numbers = LinkedHashMap<String, ContactNumber>()
        resolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER, Phone.TYPE, Phone.LABEL),
            "${Phone.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(0) ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(1), c.getString(2)).toString()
                // The same number is often stored once per linked account.
                numbers.getOrPut(number.filter(Char::isDigit).ifEmpty { number }) {
                    ContactNumber(number, label)
                }
            }
        }
        return ContactDetail(contactId, displayName, starred, numbers.values.toList(), loadPhoto(contactId))
    }

    /** Needs WRITE_CONTACTS. */
    fun setStarred(contactId: Long, starred: Boolean): Boolean = try {
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
        }
        resolver.update(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()),
            values, null, null,
        ) > 0
    } catch (e: SecurityException) {
        false
    }

    private fun loadPhoto(contactId: Long): Bitmap? = try {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString())
        ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri, false)
            ?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
}
