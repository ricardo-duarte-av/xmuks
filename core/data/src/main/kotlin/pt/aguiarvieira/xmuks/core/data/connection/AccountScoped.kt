package pt.aguiarvieira.xmuks.core.data.connection

/**
 * State that belongs to one gomuks account: cached rooms, the stream's resume point, and later the
 * database and media caches. Every implementation is wiped when the account changes — keeping any
 * of it across accounts shows one account's data under another, or asks the new server for a
 * catch-up from the old server's timeline.
 */
interface AccountScoped {
    suspend fun clearAccountData()
}
