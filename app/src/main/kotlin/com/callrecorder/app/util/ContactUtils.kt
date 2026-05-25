package com.callrecorder.app.util

import android.content.Context
import android.provider.ContactsContract

object ContactUtils {

    fun resolveCallerName(context: Context, phoneNumber: String): String {
        if (phoneNumber.isBlank()) return ""
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
