package pt.aguiarvieira.xmuks.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfile
import pt.aguiarvieira.xmuks.core.data.profile.PerMessageProfiles
import pt.aguiarvieira.xmuks.core.data.profile.UserProfile
import pt.aguiarvieira.xmuks.core.designsystem.theme.XmuksTheme
import pt.aguiarvieira.xmuks.core.network.ConnectionState

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class UserInfoScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val media = ProfileMedia({ null }, { null })

    private fun profile(
        userId: String,
        name: String,
        bio: String? = null,
        status: UserProfile.Status? = null,
        pronouns: List<UserProfile.Pronouns> = emptyList(),
    ) = UserProfile(
        userId = userId,
        displayName = name,
        avatarMxc = null,
        bannerMxc = null,
        bio = bio?.let { UserProfile.Bio(it, null) },
        status = status,
        pronouns = pronouns,
        // A time zone would put the current time in the golden.
        timezone = null,
        raw = JsonObject(emptyMap()),
    )

    private val edits = ProfileEdits({}, {}, { _, _ -> }, {}, {}, {}, {}, PersonaEdits({ _, _ -> }, {}, { _, _ -> }), {})

    private fun capture(
        name: String,
        dark: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { XmuksTheme(darkTheme = dark, dynamicColor = false, content = content) }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun someoneElse() =
        capture("user_other") {
            UserInfoScreen(
                userId = "@alice:example.org",
                state =
                    ProfileState.Loaded(
                        profile(
                            "@alice:example.org",
                            "Alice",
                            bio = "<p>Writes <b>Matrix</b> clients. <a href=\"https://example.org\">example.org</a></p>",
                            status = UserProfile.Status("at lunch", "🍜"),
                            pronouns = listOf(UserProfile.Pronouns("she/her", "en")),
                        ),
                    ),
                media = media,
                busy = false,
                error = null,
                onErrorShow = {},
                onRetry = {},
                onBack = {},
                onOpenMedia = {},
            )
        }

    @Test
    fun ownProfile() =
        capture("user_own", dark = true) {
            val personas =
                PerMessageProfiles(
                    "cat",
                    listOf(
                        PerMessageProfile("cat", "Cat mode", null, listOf(PerMessageProfile.Trigger("c:", ""))),
                        PerMessageProfile("work", "At work", null, emptyList()),
                    ),
                )
            UserInfoScreen(
                userId = "@me:example.org",
                state = ProfileState.Loaded(profile("@me:example.org", "Me")),
                media = media,
                busy = false,
                error = null,
                onErrorShow = {},
                onRetry = {},
                onBack = {},
                onOpenMedia = {},
                own = OwnProfile(edits, personas, "me · example.org", ConnectionState.Live),
            )
        }
}
