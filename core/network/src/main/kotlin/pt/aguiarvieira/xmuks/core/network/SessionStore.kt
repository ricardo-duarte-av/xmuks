package pt.aguiarvieira.xmuks.core.network

/**
 * The credentials and session token the network layer runs on. Implemented by the data layer
 * (Keystore-encrypted storage). Called from OkHttp threads, so reads must be cheap and non-suspending.
 */
interface SessionStore {
    fun credentials(): Credentials?

    fun token(): String?

    fun saveToken(token: String?)
}
