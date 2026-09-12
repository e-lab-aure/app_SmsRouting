package com.perso.smsrouting

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/** Resolution du nom affiche d'un correspondant a partir de son numero. */
object Contacts {

    private const val TAG = "Contacts"

    /** Renvoie le nom du contact correspondant au numero, ou null s'il est inconnu. */
    fun displayName(context: Context, phoneNumber: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Acces aux contacts refuse", e)
            null
        }
    }
}
