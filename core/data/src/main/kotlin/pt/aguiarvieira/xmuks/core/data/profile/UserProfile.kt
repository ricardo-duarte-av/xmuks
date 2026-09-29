package pt.aguiarvieira.xmuks.core.data.profile

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pt.aguiarvieira.xmuks.core.data.timeline.obj
import pt.aguiarvieira.xmuks.core.data.timeline.str

/**
 * A user's global profile as `get_profile` returns it: the classic name and avatar plus the
 * extended fields (MSC4133) other clients have settled on. Unknown fields stay in [raw].
 */
data class UserProfile(
    val userId: String,
    val displayName: String?,
    val avatarMxc: String?,
    val bannerMxc: String?,
    val bio: Bio?,
    val status: Status?,
    val pronouns: List<Pronouns>,
    val timezone: String?,
    val raw: JsonObject,
) {
    /** [html] to show; [editSource] (markdown) only for our own, and only when gomuks has one. */
    data class Bio(
        val html: String,
        val editSource: String?,
    )

    /** MSC4426: a short line and an emoji. */
    data class Status(
        val text: String,
        val emoji: String?,
    )

    data class Pronouns(
        val summary: String,
        val language: String?,
    )

    companion object {
        /** `get_profile`'s reply: `{profile: {…fields}, bio?: {html, edit_source}}`. */
        fun parse(
            userId: String,
            response: JsonObject,
        ): UserProfile {
            val profile = response.obj("profile") ?: JsonObject(emptyMap())
            return UserProfile(
                userId = userId,
                displayName = profile.str(ProfileFields.DISPLAY_NAME)?.takeIf { it.isNotBlank() },
                avatarMxc = profile.str(ProfileFields.AVATAR)?.takeIf { it.startsWith("mxc://") },
                bannerMxc = profile.str(ProfileFields.BANNER)?.takeIf { it.startsWith("mxc://") },
                bio = bioOf(response.obj("bio"), profile),
                status = statusOf(profile),
                pronouns = pronounsOf(profile[ProfileFields.PRONOUNS]),
                timezone =
                    (profile.str(ProfileFields.TIMEZONE) ?: profile.str(ProfileFields.TIMEZONE_UNSTABLE))
                        ?.takeIf { it.isNotBlank() },
                raw = profile,
            )
        }

        /**
         * gomuks sanitises one biography (`gay.fomx.biography`) and hands it over as HTML; the others
         * turn up raw. Our renderer only knows a safe subset of HTML, so raw is fine to show.
         */
        private fun bioOf(
            gomuks: JsonObject?,
            profile: JsonObject,
        ): Bio? {
            gomuks?.str("html")?.takeIf { it.isNotBlank() }?.let {
                return Bio(it, gomuks.str("edit_source")?.takeIf(String::isNotBlank))
            }
            val html =
                extensibleHtml(profile[ProfileFields.BIO_STABLE])
                    ?: profile.obj("chat.commet.profile_bio")?.let {
                        it.str("formatted_body") ?: it.str("body")?.let(::plainToHtml)
                    } ?: profile.str("moe.sable.app.bio")
            return html?.takeIf { it.isNotBlank() }?.let { Bio(it, null) }
        }

        /** An extensible text container (`{"m.text": [{body, mimetype}]}`): HTML first, else plain. */
        private fun extensibleHtml(value: JsonElement?): String? {
            val reprs = ((value as? JsonObject)?.get("m.text") as? JsonArray)?.mapNotNull { it as? JsonObject }
            reprs ?: return null
            reprs.firstOrNull { it.str("mimetype") == "text/html" }?.str("body")?.let { return it }
            return reprs
                .firstOrNull { it.str("mimetype").let { m -> m == null || m == "text/plain" } }
                ?.str("body")
                ?.let(::plainToHtml)
        }

        private fun statusOf(profile: JsonObject): Status? {
            val status = profile.obj(ProfileFields.STATUS_STABLE) ?: profile.obj(ProfileFields.STATUS) ?: return null
            val text = status.str("text").orEmpty()
            val emoji = status.str("emoji")?.takeIf { it.isNotBlank() }
            return if (text.isBlank() && emoji == null) null else Status(text, emoji)
        }

        private fun pronounsOf(value: JsonElement?): List<Pronouns> =
            (value as? JsonArray).orEmpty().mapNotNull { set ->
                val obj = set as? JsonObject ?: return@mapNotNull null
                val summary = (obj["summary"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                summary?.let { Pronouns(it, obj.str("language")) }
            }

        private fun plainToHtml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>")
    }
}

/** Profile field names, as gomuks (web) and the clients around it write them. */
object ProfileFields {
    const val DISPLAY_NAME = "displayname"
    const val AVATAR = "avatar_url"

    /** Commet's banner; the only one in use so far. */
    const val BANNER = "chat.commet.profile_banner"

    /** MSC4440. gomuks writes the unstable field (through its `_gomuks_bio` pseudo-field) and reads it. */
    const val BIO_STABLE = "m.biography"
    const val BIO_UNSTABLE = "gay.fomx.biography"

    /** Markdown in, extensible text (`gay.fomx.biography`) out: gomuks renders it. */
    const val BIO_GOMUKS = "_gomuks_bio"

    /** MSC4426. */
    const val STATUS = "org.matrix.msc4426.status"
    const val STATUS_STABLE = "m.status"
    const val PRONOUNS = "io.fsky.nyx.pronouns"
    const val TIMEZONE = "m.tz"
    const val TIMEZONE_UNSTABLE = "us.cloke.msc4175.tz"
}
