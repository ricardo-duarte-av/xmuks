package pt.aguiarvieira.xmuks.core.data.contacts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import pt.aguiarvieira.xmuks.core.data.R
import pt.aguiarvieira.xmuks.core.notify.PeopleUris

/** Someone we have a DM with, as a phone contact. */
data class ContactPerson(
    val userId: String,
    val name: String,
    /** JPEG, when they have an avatar. */
    val photo: ByteArray? = null,
) {
    override fun equals(other: Any?) = other is ContactPerson && other.userId == userId && other.name == name

    override fun hashCode() = userId.hashCode() * 31 + name.hashCode()
}

/**
 * xmuks' contacts in the phone's Contacts: one raw contact per DM partner, under xmuks' own
 * account (so its rows show with xmuks' icon and labels, and nothing touches other accounts'
 * contacts), each with Message, Voice call and Video call actions. The phone's Contacts app merges
 * one with a phone contact of the same name, or the user links them by hand.
 */
class PhoneContacts(
    private val context: Context,
) {
    private val resolver get() = context.contentResolver

    private fun account(userId: String) = Account(userId, ACCOUNT_TYPE)

    /** Makes the Contacts rows match [people] exactly (adds, renames, re-photographs, removes). */
    fun sync(
        ownUserId: String,
        people: List<ContactPerson>,
    ) {
        val account = account(ownUserId)
        AccountManager.get(context).addAccountExplicitly(account, null, null)
        // Another account's leftovers (a different login) go first.
        AccountManager
            .get(context)
            .getAccountsByType(ACCOUNT_TYPE)
            .filter { it != account }
            .forEach { remove(it) }
        val existing = existing(account)
        val labelled = labelled()
        val wanted = people.associateBy { it.userId }
        val ops = ArrayList<ContentProviderOperation>()
        (existing.keys - wanted.keys).forEach { userId ->
            ops +=
                ContentProviderOperation
                    .newDelete(
                        syncUri(RawContacts.CONTENT_URI)
                    ).withSelection("${RawContacts._ID} = ?", arrayOf(existing.getValue(userId).toString()))
                    .build()
        }
        for (person in people) {
            val rawId = existing[person.userId]
            if (rawId ==
                null
            ) {
                insert(ops, account, person)
            } else {
                update(ops, rawId, person, rewriteActions = rawId !in labelled)
            }
        }
        ops.chunked(BATCH).forEach { resolver.applyBatch(AUTHORITY, ArrayList(it)) }
    }

    /** Removes every xmuks contact (and the account): the setting was turned off, or we logged out. */
    fun clear() {
        AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).forEach { remove(it) }
    }

    private fun remove(account: Account) {
        resolver.delete(
            syncUri(RawContacts.CONTENT_URI),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
            arrayOf(account.type, account.name),
        )
        AccountManager.get(context).removeAccountExplicitly(account)
    }

    /** Raw contacts whose Message row already carries today's label (theirs need no rewrite). */
    private fun labelled(): Set<Long> =
        resolver
            .query(
                Data.CONTENT_URI,
                arrayOf(Data.RAW_CONTACT_ID),
                "${Data.MIMETYPE} = ? AND ${Data.DATA3} = ?",
                arrayOf(PeopleUris.MIME_MESSAGE, context.getString(R.string.contact_message)),
                null,
            )?.use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }
            .orEmpty()

    /** Our raw contacts by user, from their source id. */
    private fun existing(account: Account): Map<String, Long> =
        resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.SOURCE_ID),
                "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.DELETED} = 0",
                arrayOf(account.type, account.name),
                null,
            )?.use { c ->
                buildMap { while (c.moveToNext()) c.getString(1)?.let { put(it, c.getLong(0)) } }
            }.orEmpty()

    private fun insert(
        ops: MutableList<ContentProviderOperation>,
        account: Account,
        person: ContactPerson,
    ) {
        val back = ops.size
        ops +=
            ContentProviderOperation
                .newInsert(syncUri(RawContacts.CONTENT_URI))
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.SOURCE_ID, person.userId)
                .build()

        fun row() =
            ContentProviderOperation
                .newInsert(
                    syncUri(Data.CONTENT_URI)
                ).withValueBackReference(Data.RAW_CONTACT_ID, back)
        ops +=
            row()
                .withValue(
                    Data.MIMETYPE,
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                ).withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, person.name)
                .build()
        person.photo?.let {
            ops +=
                row()
                    .withValue(
                        Data.MIMETYPE,
                        CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    ).withValue(CommonDataKinds.Photo.PHOTO, it)
                    .build()
        }
        actions().forEach { (mime, label) -> ops += actionRow(row(), mime, label, person).build() }
    }

    /**
     * One action row: DATA3 is what Contacts shows for it ("Voice call"), DATA2 the line under it
     * (the Matrix ID), DATA1 the `matrix:u/` URI every entry point reads.
     */
    private fun actionRow(
        builder: ContentProviderOperation.Builder,
        mime: String,
        label: String,
        person: ContactPerson,
    ) = builder
        .withValue(Data.MIMETYPE, mime)
        .withValue(Data.DATA1, PeopleUris.matrix(person.userId))
        .withValue(Data.DATA2, person.userId)
        .withValue(Data.DATA3, label)

    /** Name, photo and the action rows follow (rows are rewritten: older ones were labelled differently). */
    private fun update(
        ops: MutableList<ContentProviderOperation>,
        rawId: Long,
        person: ContactPerson,
        rewriteActions: Boolean,
    ) {
        ops +=
            ContentProviderOperation
                .newUpdate(syncUri(Data.CONTENT_URI))
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                    arrayOf(rawId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                ).withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, person.name)
                .build()
        if (rewriteActions) rewriteActions(ops, rawId, person)
        val photo = person.photo ?: return
        ops +=
            ContentProviderOperation
                .newDelete(syncUri(Data.CONTENT_URI))
                .withSelection(
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                    arrayOf(rawId.toString(), CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                ).build()
        ops +=
            ContentProviderOperation
                .newInsert(syncUri(Data.CONTENT_URI))
                .withValue(Data.RAW_CONTACT_ID, rawId)
                .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Photo.PHOTO, photo)
                .build()
    }

    private fun rewriteActions(
        ops: MutableList<ContentProviderOperation>,
        rawId: Long,
        person: ContactPerson,
    ) {
        for ((mime, label) in actions()) {
            ops +=
                ContentProviderOperation
                    .newDelete(syncUri(Data.CONTENT_URI))
                    .withSelection(
                        "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                        arrayOf(rawId.toString(), mime)
                    ).build()
            val insert =
                ContentProviderOperation
                    .newInsert(
                        syncUri(Data.CONTENT_URI)
                    ).withValue(Data.RAW_CONTACT_ID, rawId)
            ops += actionRow(insert, mime, label, person).build()
        }
    }

    private fun actions() =
        listOf(
            PeopleUris.MIME_MESSAGE to context.getString(R.string.contact_message),
            PeopleUris.MIME_VOICE_CALL to context.getString(R.string.contact_voice_call),
            PeopleUris.MIME_VIDEO_CALL to context.getString(R.string.contact_video_call),
        )

    private fun syncUri(uri: android.net.Uri) =
        uri.buildUpon().appendQueryParameter(android.provider.ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()

    companion object {
        const val ACCOUNT_TYPE = "pt.aguiarvieira.xmuks"
        private const val AUTHORITY = android.provider.ContactsContract.AUTHORITY
        private const val BATCH = 300
    }
}
