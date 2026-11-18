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
    /** Their avatar (mxc), when they have one: its photo is fetched only when it changed. */
    val avatarUrl: String? = null,
    /**
     * The network their DM is bridged to ("WhatsApp"), or null for a Matrix user. A bridged person
     * gets only Message (named for the network, so the same person on two bridges reads apart):
     * the bridges don't carry Matrix calls.
     */
    val network: String? = null,
)

/** One of our raw contacts, with what it was last written from (SYNC1: avatar, SYNC2: name). */
private data class Existing(
    val rawId: Long,
    val avatarUrl: String?,
    val name: String?,
)

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

    /**
     * Makes the Contacts rows match [people] exactly (adds, renames, re-photographs, removes). Only
     * what changed is written, and a photo is fetched ([photo], JPEG) only for a new avatar: this
     * runs whenever the DMs change, and rewriting every contact each time costs real battery
     * (ours, and the Contacts provider's re-processing every photo).
     */
    suspend fun sync(
        ownUserId: String,
        people: List<ContactPerson>,
        /** The other signed-in accounts' users: their contacts stay. */
        others: Set<String> = emptySet(),
        photo: suspend (url: String) -> ByteArray? = { null },
    ) {
        val account = account(ownUserId)
        AccountManager.get(context).addAccountExplicitly(account, null, null)
        // A signed-out account's leftovers go first.
        AccountManager
            .get(context)
            .getAccountsByType(ACCOUNT_TYPE)
            .filter { it != account && it.name !in others }
            .forEach { remove(it) }
        val existing = existing(account)
        val labels = messageLabels()
        val wanted = people.associateBy { it.userId }
        val ops = ArrayList<ContentProviderOperation>()
        (existing.keys - wanted.keys).forEach { userId ->
            ops +=
                ContentProviderOperation
                    .newDelete(
                        syncUri(RawContacts.CONTENT_URI)
                    ).withSelection("${RawContacts._ID} = ?", arrayOf(existing.getValue(userId).rawId.toString()))
                    .build()
        }
        for (person in people) {
            val known = existing[person.userId]
            if (known == null) {
                insert(ops, account, person, person.avatarUrl?.let { photo(it) })
            } else {
                val avatar = person.avatarUrl
                // A new avatar replaces the photo; a gone one takes it away; one that can't be
                // fetched now leaves it be (and is tried again next time).
                val change =
                    when {
                        known.avatarUrl == avatar -> null
                        avatar == null -> PhotoChange(null)
                        else -> photo(avatar)?.let { PhotoChange(it) }
                    }
                update(
                    ops,
                    known,
                    person,
                    rewriteActions = labels[known.rawId] != messageLabel(person),
                    photo = change,
                )
            }
        }
        ops.chunked(BATCH).forEach { resolver.applyBatch(AUTHORITY, ArrayList(it)) }
    }

    /** Removes every xmuks contact (and the account): the setting was turned off, or we logged out. */
    fun clear() {
        AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).forEach { remove(it) }
    }

    /** Removes [ownUserId]'s contacts (and its account) only: another account's stay. */
    fun clear(ownUserId: String) {
        AccountManager
            .get(context)
            .getAccountsByType(ACCOUNT_TYPE)
            .filter { it.name == ownUserId }
            .forEach { remove(it) }
    }

    private fun remove(account: Account) {
        resolver.delete(
            syncUri(RawContacts.CONTENT_URI),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
            arrayOf(account.type, account.name),
        )
        AccountManager.get(context).removeAccountExplicitly(account)
    }

    /** Each raw contact's Message row label: when it isn't what it should be now, the rows are rewritten. */
    private fun messageLabels(): Map<Long, String?> =
        resolver
            .query(
                Data.CONTENT_URI,
                arrayOf(Data.RAW_CONTACT_ID, Data.DATA3),
                "${Data.MIMETYPE} = ?",
                arrayOf(PeopleUris.MIME_MESSAGE),
                null,
            )?.use { c -> buildMap { while (c.moveToNext()) put(c.getLong(0), c.getString(1)) } }
            .orEmpty()

    /** Our raw contacts by user, from their source id. */
    private fun existing(account: Account): Map<String, Existing> =
        resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.SOURCE_ID, RawContacts.SYNC1, RawContacts.SYNC2),
                "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.DELETED} = 0",
                arrayOf(account.type, account.name),
                null,
            )?.use { c ->
                buildMap<String, Existing> {
                    while (c.moveToNext()) {
                        val userId = c.getString(c.getColumnIndexOrThrow(RawContacts.SOURCE_ID)) ?: continue
                        val known =
                            Existing(
                                rawId = c.getLong(c.getColumnIndexOrThrow(RawContacts._ID)),
                                avatarUrl = c.getString(c.getColumnIndexOrThrow(RawContacts.SYNC1)),
                                name = c.getString(c.getColumnIndexOrThrow(RawContacts.SYNC2)),
                            )
                        put(userId, known)
                    }
                }
            }.orEmpty()

    private fun insert(
        ops: MutableList<ContentProviderOperation>,
        account: Account,
        person: ContactPerson,
        photo: ByteArray?,
    ) {
        val back = ops.size
        ops +=
            ContentProviderOperation
                .newInsert(syncUri(RawContacts.CONTENT_URI))
                .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                .withValue(RawContacts.ACCOUNT_NAME, account.name)
                .withValue(RawContacts.SOURCE_ID, person.userId)
                .withValue(RawContacts.SYNC1, person.avatarUrl.takeIf { photo != null })
                .withValue(RawContacts.SYNC2, person.name)
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
        photo?.let {
            ops +=
                row()
                    .withValue(
                        Data.MIMETYPE,
                        CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    ).withValue(CommonDataKinds.Photo.PHOTO, it)
                    .build()
        }
        actions(person).forEach { (mime, label) -> ops += actionRow(row(), mime, label, person).build() }
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

    /** The photo to put in place of the old one: null [jpeg] takes it away. */
    private class PhotoChange(
        val jpeg: ByteArray?,
    )

    /**
     * Name, photo and the action rows follow, each only when it changed (rows are rewritten: older
     * ones were labelled differently). A null [photo] leaves the photo be.
     */
    private fun update(
        ops: MutableList<ContentProviderOperation>,
        known: Existing,
        person: ContactPerson,
        rewriteActions: Boolean,
        photo: PhotoChange?,
    ) {
        val rawId = known.rawId
        if (known.name != person.name) {
            ops +=
                ContentProviderOperation
                    .newUpdate(syncUri(Data.CONTENT_URI))
                    .withSelection(
                        "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                        arrayOf(rawId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    ).withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, person.name)
                    .build()
        }
        if (rewriteActions) rewriteActions(ops, rawId, person)
        if (photo != null) {
            ops +=
                ContentProviderOperation
                    .newDelete(syncUri(Data.CONTENT_URI))
                    .withSelection(
                        "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                        arrayOf(rawId.toString(), CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    ).build()
            photo.jpeg?.let {
                ops +=
                    ContentProviderOperation
                        .newInsert(syncUri(Data.CONTENT_URI))
                        .withValue(Data.RAW_CONTACT_ID, rawId)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Photo.PHOTO, it)
                        .build()
            }
        }
        // What it's now written from (an avatar whose photo couldn't be fetched isn't recorded).
        val avatar = if (photo == null) known.avatarUrl else person.avatarUrl
        if (known.name != person.name || known.avatarUrl != avatar) {
            ops +=
                ContentProviderOperation
                    .newUpdate(syncUri(RawContacts.CONTENT_URI))
                    .withSelection("${RawContacts._ID} = ?", arrayOf(rawId.toString()))
                    .withValue(RawContacts.SYNC1, avatar)
                    .withValue(RawContacts.SYNC2, person.name)
                    .build()
        }
    }

    private fun rewriteActions(
        ops: MutableList<ContentProviderOperation>,
        rawId: Long,
        person: ContactPerson,
    ) {
        // Every kind goes (a bridged person keeps no call rows); what this person has comes back.
        for (mime in ACTION_MIMES) {
            ops +=
                ContentProviderOperation
                    .newDelete(syncUri(Data.CONTENT_URI))
                    .withSelection(
                        "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                        arrayOf(rawId.toString(), mime)
                    ).build()
        }
        for ((mime, label) in actions(person)) {
            val insert =
                ContentProviderOperation
                    .newInsert(
                        syncUri(Data.CONTENT_URI)
                    ).withValue(Data.RAW_CONTACT_ID, rawId)
            ops += actionRow(insert, mime, label, person).build()
        }
    }

    /** What a person's contact offers: Message, and calls unless they're on a bridge. */
    private fun actions(person: ContactPerson) =
        if (person.network != null) {
            listOf(PeopleUris.MIME_MESSAGE to messageLabel(person))
        } else {
            listOf(
                PeopleUris.MIME_MESSAGE to messageLabel(person),
                PeopleUris.MIME_VOICE_CALL to context.getString(R.string.contact_voice_call),
                PeopleUris.MIME_VIDEO_CALL to context.getString(R.string.contact_video_call),
            )
        }

    private fun messageLabel(person: ContactPerson): String =
        person.network?.let { context.getString(R.string.contact_message_on, it) }
            ?: context.getString(R.string.contact_message)

    private fun syncUri(uri: android.net.Uri) =
        uri.buildUpon().appendQueryParameter(android.provider.ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()

    companion object {
        const val ACCOUNT_TYPE = "pt.aguiarvieira.xmuks"
        private const val AUTHORITY = android.provider.ContactsContract.AUTHORITY
        private const val BATCH = 300
        private val ACTION_MIMES =
            listOf(PeopleUris.MIME_MESSAGE, PeopleUris.MIME_VOICE_CALL, PeopleUris.MIME_VIDEO_CALL)
    }
}
