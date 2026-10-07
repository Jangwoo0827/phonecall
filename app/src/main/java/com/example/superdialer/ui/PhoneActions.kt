package com.example.superdialer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast

fun Context.sendSms(number: String) {
    startActivityOrToast(
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")),
        "문자 앱을 찾을 수 없습니다.",
    )
}

fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivityOrToast(Intent.createChooser(send, "공유"), "공유할 앱이 없습니다.")
}

/** Opens the system "add to contacts" screen with [number] filled in. */
fun Context.addContact(number: String) {
    startActivityOrToast(
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
        "연락처 앱을 찾을 수 없습니다.",
    )
}

private fun Context.startActivityOrToast(intent: Intent, failureMessage: String) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}
