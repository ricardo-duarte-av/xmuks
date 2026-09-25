import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Enables Compose on an Android application or library. Apply after the application/library
 * convention. The Compose compiler comes from the Kotlin compose plugin; the UI artifacts are
 * aligned by the alpha BOM, with material3 pinned separately for the Expressive APIs.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.getByType<CommonExtension>().apply {
                buildFeatures.compose = true
            }

            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom-alpha"))
                add("implementation", bom)
                add("testImplementation", bom)
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
