package com.example.superdialer.contacts

import android.graphics.Bitmap

/** List item: only contacts that have at least one phone number. */
data class Contact(
    val id: Long,
    val name: String,
    val starred: Boolean,
    val numbers: List<String>,
)

data class ContactNumber(val number: String, val label: String)

data class ContactDetail(
    val id: Long,
    val name: String,
    val starred: Boolean,
    val numbers: List<ContactNumber>,
    val photo: Bitmap?,
)

data class ContactSection(val header: String, val contacts: List<Contact>)
