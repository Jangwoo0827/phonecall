package com.example.superdialer

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract

/**
 * Rows the tests put into the phone's call log / contacts. Always use made-up numbers (010-7000-xxxx) and names, and
 * remove them again in @After, so the tests pass (and leave nothing behind) whatever else is on the device.
 */
object TestData {
    const val PREFIX = "01070"

    fun addCall(context: Context, number: String, type: Int, dateMillis: Long = System.currentTimeMillis(), seconds: Int = 12) {
        context.contentResolver.insert(CallLog.Calls.CONTENT_URI, ContentValues().apply {
            put(CallLog.Calls.NUMBER, number)
            put(CallLog.Calls.TYPE, type)
            put(CallLog.Calls.DATE, dateMillis)
            put(CallLog.Calls.DURATION, seconds)
        })
    }

    /** Removes every call whose number starts with [PREFIX]. */
    fun removeTestCalls(context: Context) {
        context.contentResolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls.NUMBER} LIKE ?", arrayOf("$PREFIX%"))
    }

    fun addContact(context: Context, name: String, number: String) {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build(),
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    /** Removes the contact(s) with exactly this name (a hard delete, not "marked deleted"). */
    fun removeContact(context: Context, name: String) {
        val uri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
        val ids = ArrayList<String>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY} = ?", arrayOf(name), null,
        )?.use { while (it.moveToNext()) ids += it.getString(0) }
        ids.forEach { context.contentResolver.delete(uri, "${ContactsContract.RawContacts._ID} = ?", arrayOf(it)) }
    }
}
