import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * JVM screenshot tests: Roborazzi on Robolectric's native graphics, so Compose UI renders to PNG
 * without an emulator. Goldens live in `src/test/screenshots`; `./gradlew recordRoborazziDebug`
 * rewrites them, `verifyRoborazziDebug` (CI) fails on any pixel diff.
 */
class ScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")

            dependencies {
                add("testImplementation", libs.lib("robolectric"))
                add("testImplementation", libs.lib("roborazzi"))
                add("testImplementation", libs.lib("roborazzi-compose"))
                add("testImplementation", libs.lib("roborazzi-junit-rule"))
                add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
                add("testImplementation", libs.lib("androidx-test-ext-junit"))
                add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
