package pt.aguiarvieira.xmuks.core.data.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts

/**
 * Linking xmuks' contact for someone to a phone contact by hand, for names Android doesn't match by
 * itself (a bridged "Thalita (WA)" and the phone's "Thalita"): an aggregation exception, so our rows
 * stay ours and theirs stay theirs, and it can be undone. The link is remembered on our raw contact.
 */
class ContactLinks(
    context: Context,
) {
    private val resolver = context.contentResolver

    /** Whether [userId] is one of our contacts (so it can be linked to a phone contact). */
    fun has(userId: String): Boolean = rawContactOf(userId) != null

    /** The phone contact [userId]'s contact was linked to by hand (its lookup URI), if any. */
    fun linkedTo(userId: String): String? {
        val raw = rawContactOf(userId) ?: return null
        return resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.SYNC1),
                "${RawContacts._ID} = ?",
                arrayOf(raw.toString()),
                null
            )?.use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotEmpty() } else null }
    }

    /**
     * Makes [userId]'s contact and the picked phone contact one person in Contacts (an aggregation
     * exception: our rows stay ours, theirs stay theirs, and it can be undone). For names Android
     * doesn't match by itself (a bridged "Thalita (WA)" and the phone's "Thalita").
     */
    fun link(
        userId: String,
        picked: Uri,
    ): Boolean {
        val ours = rawContactOf(userId) ?: return false
        val lookup =
            ContactsContract.Contacts
                .getLookupUri(resolver, picked) ?: picked
        val theirs = rawContactsOf(lookup).filter { it != ours }
        if (theirs.isEmpty()) return false
        val ops =
            theirs.map {
                aggregation(
                    ours,
                    it,
                    ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER
                )
            } +
                ContentProviderOperation
                    .newUpdate(syncAdapter(RawContacts.CONTENT_URI))
                    .withSelection("${RawContacts._ID} = ?", arrayOf(ours.toString()))
                    .withValue(RawContacts.SYNC1, lookup.toString())
                    .build()
        return runCatching { resolver.applyBatch(AUTHORITY, ArrayList(ops)) }.isSuccess
    }

    /** Undoes [link]: kept apart from then on (not left to Android, which might join them again). */
    fun unlink(userId: String): Boolean {
        val ours = rawContactOf(userId) ?: return false
        val lookup = linkedTo(userId)?.let(Uri::parse)
        val theirs = lookup?.let(::rawContactsOf).orEmpty().filter { it != ours }
        val ops =
            theirs.map {
                aggregation(
                    ours,
                    it,
                    ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE
                )
            } +
                ContentProviderOperation
                    .newUpdate(syncAdapter(RawContacts.CONTENT_URI))
                    .withSelection("${RawContacts._ID} = ?", arrayOf(ours.toString()))
                    .withValue(RawContacts.SYNC1, null)
                    .build()
        return runCatching { resolver.applyBatch(AUTHORITY, ArrayList(ops)) }.isSuccess
    }

    private fun aggregation(
        a: Long,
        b: Long,
        type: Int,
    ) = ContentProviderOperation
        .newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
        .withValue(ContactsContract.AggregationExceptions.TYPE, type)
        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, a)
        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, b)
        .build()

    private fun rawContactOf(userId: String): Long? =
        resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID),
                "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.SOURCE_ID} = ? AND ${RawContacts.DELETED} = 0",
                arrayOf(ACCOUNT_TYPE, userId),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** The raw contacts behind a contact (lookup or contact URI). */
    private fun rawContactsOf(contact: Uri): List<Long> {
        val contactId =
            resolver
                .query(contact, arrayOf(ContactsContract.Contacts._ID), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return emptyList()
        return resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID),
                "${RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
            .orEmpty()
    }

    private fun syncAdapter(uri: Uri) =
        uri.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()

    private companion object {
        const val ACCOUNT_TYPE = PhoneContacts.ACCOUNT_TYPE
        const val AUTHORITY = ContactsContract.AUTHORITY
    }
}
