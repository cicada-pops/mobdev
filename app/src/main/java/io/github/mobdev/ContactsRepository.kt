package io.github.mobdev

import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import androidx.core.database.getStringOrNull

@SuppressLint("Range")
fun Context.fetchAllContacts(): List<Contact> {
    Log.d("FETCH", "fetchAllContacts called")

    val emailMap = mutableMapOf<String, String>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        ),
        null, null, null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val contactId = cursor.getStringOrNull(
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.CONTACT_ID)
            ) ?: continue
            val email = cursor.getStringOrNull(
                cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            )
            if (email != null && contactId !in emailMap) {
                emailMap[contactId] = email
            }
        }
    }

    contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null, null, null, null
    ).use { cursor ->
        if (cursor == null) return emptyList()
        return buildList {
            while (cursor.moveToNext()) {
                val contactId = cursor.getStringOrNull(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                )
                val name = cursor.getStringOrNull(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                )
                val phoneNumber = cursor.getStringOrNull(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                )
                val email = contactId?.let { emailMap[it] }
                add(Contact(name, phoneNumber, email))
            }
        }
    }
}
