package pt.aguiarvieira.xmuks.core.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * What a Matrix user is to Android's people features (Person.setUri): their phone contact when
 * xmuks put them in Contacts (then DND's priority contacts, the People widget and conversation
 * settings treat messages and calls from them as from that contact), else a `matrix:u/` URI, the
 * same identity our contact rows and Telecom calls carry.
 */
object PeopleUris {
    /** Our Contacts row that opens a DM with someone; its DATA1 is [matrix] of them. */
    const val MIME_MESSAGE = "vnd.android.cursor.item/vnd.pt.aguiarvieira.xmuks.message"
    const val MIME_VOICE_CALL = "vnd.android.cursor.item/vnd.pt.aguiarvieira.xmuks.call"
    const val MIME_VIDEO_CALL = "vnd.android.cursor.item/vnd.pt.aguiarvieira.xmuks.videocall"

    fun matrix(userId: String) = "matrix:u/${userId.removePrefix("@")}"

    /** The user back from a [matrix] URI. */
    fun userOf(uri: String): String? = uri.removePrefix("matrix:u/").takeIf { it != uri && ':' in it }?.let { "@$it" }

    fun forUser(
        context: Context,
        userId: String,
    ): String = contactOf(context, userId) ?: matrix(userId)

    /** The phone contact xmuks made for [userId] (a lookup URI), when there is one and we may read it. */
    fun contactOf(
        context: Context,
        userId: String,
    ): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return runCatching {
            context.contentResolver
                .query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY),
                    "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.Data.DATA1} = ?",
                    arrayOf(MIME_MESSAGE, matrix(userId)),
                    null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))?.toString()
                }
        }.getOrNull()
    }
}
