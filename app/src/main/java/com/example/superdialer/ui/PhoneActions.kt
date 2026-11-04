package com.example.superdialer.ui

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

fun Context.sendSms(number: String) {
    startActivityOrToast(
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")),
        str(R.string.t_can_t_find_the_messages_app),
    )
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivityOrToast(Intent.createChooser(send, str(R.string.t_share)), str(R.string.t_no_app_to_share_with))
}

/** Opens the system "add to contacts" screen with [number] filled in. */
fun Context.addContact(number: String) {
    startActivityOrToast(
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
        str(R.string.t_can_t_find_the_contacts_app),
    )
}

private fun Context.startActivityOrToast(intent: Intent, failureMessage: String) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}
