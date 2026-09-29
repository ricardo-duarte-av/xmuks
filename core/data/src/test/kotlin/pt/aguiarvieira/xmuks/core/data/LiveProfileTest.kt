package pt.aguiarvieira.xmuks.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.ProfileFields
import pt.aguiarvieira.xmuks.core.data.profile.ProfileRepository
import pt.aguiarvieira.xmuks.core.data.sync.SyncIngestor
import pt.aguiarvieira.xmuks.core.database.XmuksDatabase
import pt.aguiarvieira.xmuks.core.network.AuthApi
import pt.aguiarvieira.xmuks.core.network.AuthInterceptor
import pt.aguiarvieira.xmuks.core.network.Credentials
import pt.aguiarvieira.xmuks.core.network.ExecClient
import pt.aguiarvieira.xmuks.core.network.SessionStore
import pt.aguiarvieira.xmuks.core.network.parseServerUrl
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Opt-in, and it WRITES to the account's own profile — use a test account only. Sets status,
 * pronouns, time zone and biography, uploads an image, saves per-message profiles, checks what
 * the server then returns, and puts every field back as it was.
 *
 *     XMUKS_LIVE_SERVER=https://… XMUKS_LIVE_USER=… XMUKS_LIVE_PASS=… XMUKS_LIVE_PROFILE_USER='@test:server' \
 *         ./gradlew :core:data:testDebugUnitTest --tests '*LiveProfileTest*' -i
 */
@RunWith(AndroidJUnit4::class)
class LiveProfileTest {
    private val server = System.getenv("XMUKS_LIVE_SERVER")?.let(::parseServerUrl)
    private val user = System.getenv("XMUKS_LIVE_USER")
    private val pass = System.getenv("XMUKS_LIVE_PASS")

    /** The Matrix account behind the login: a second guard that this is the test account. */
    private val profileUser = System.getenv("XMUKS_LIVE_PROFILE_USER")

    private fun repository(): ProfileRepository {
        val session =
            object : SessionStore {
                var token: String? = null

                override fun credentials() = Credentials(server!!, user!!, pass!!)

                override fun token() = token

                override fun saveToken(token: String?) {
                    this.token = token
                }
            }
        val plain = OkHttpClient.Builder().readTimeout(45, TimeUnit.SECONDS).build()
        val http = plain.newBuilder().addInterceptor(AuthInterceptor(session, AuthApi(plain))).build()
        val db = XmuksDatabase.build(ApplicationProvider.getApplicationContext(), name = null, driver = AndroidSQLiteDriver())
        return ProfileRepository(ExecClient(http, { server }, Dispatchers.IO), http, { server }, db, SyncIngestor(db), Dispatchers.IO)
    }

    @Test
    fun `edit the profile, upload, save per-message profiles, restore`() =
        runBlocking {
            assumeTrue(server != null && user != null && pass != null && profileUser != null)
            val repo = repository()
            val before = repo.load(profileUser!!).getOrThrow()
            val touched =
                listOf(ProfileFields.STATUS, ProfileFields.PRONOUNS, ProfileFields.TIMEZONE, ProfileFields.BIO_UNSTABLE)
            try {
                val status = buildJsonObject { put("text", JsonPrimitive("live test")) }
                repo.setField(ProfileFields.STATUS, JsonObject(status + ("emoji" to JsonPrimitive("🧪")))).getOrThrow()
                val pronouns = buildJsonObject { put("summary", JsonPrimitive("they/them")) }
                repo.setField(ProfileFields.PRONOUNS, JsonArray(listOf(pronouns))).getOrThrow()
                repo.setText(ProfileFields.TIMEZONE, "Europe/Lisbon").getOrThrow()
                repo.setBio("a **live** test").getOrThrow()

                val after = repo.load(profileUser).getOrThrow()
                assertEquals("live test", after.status?.text)
                assertEquals("🧪", after.status?.emoji)
                assertEquals("they/them", after.pronouns.single().summary)
                assertEquals("Europe/Lisbon", after.timezone)
                assertTrue(
                    after.bio?.html.orEmpty(),
                    after.bio
                        ?.html
                        .orEmpty()
                        .contains("<strong>live</strong>")
                )
                assertEquals("a **live** test", after.bio?.editSource)

                val mxc = repo.upload("pixel.png", "image/png", PIXEL).getOrThrow()
                assertTrue(mxc, mxc.startsWith("mxc://"))

                val personas =
                    PerMessageProfiles(
                        null,
                        listOf(PerMessageProfile("live", "Live test", mxc, listOf(PerMessageProfile.Trigger("lt:", "")))),
                    )
                repo.savePerMessageProfiles(personas).getOrThrow()
            } finally {
                touched.forEach { field -> repo.setField(field, before.raw[field]) }
                repo.savePerMessageProfiles(PerMessageProfiles.EMPTY)
            }
            val restored = repo.load(profileUser).getOrThrow()
            touched.forEach { assertEquals(it, before.raw[it], restored.raw[it]) }
        }

    private companion object {
        /** A 1×1 transparent PNG. */
        val PIXEL: ByteArray =
            Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
            )
    }
}
