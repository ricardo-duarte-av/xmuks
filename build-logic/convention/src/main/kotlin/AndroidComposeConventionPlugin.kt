import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

            // Shared stability configuration (see compose_stability.conf). Passed as a raw compiler
            // option so build-logic needn't compile against the Compose Gradle plugin's API.
            val stability =
                rootProject.layout.projectDirectory
                    .file("compose_stability.conf")
                    .asFile
            // `-PcomposeReports=true` writes the compiler's stability/skippability reports to build/compose.
            val reports = providers.gradleProperty("composeReports").map(String::toBoolean).getOrElse(false)
            val reportsDir =
                layout.buildDirectory
                    .dir("compose")
                    .get()
                    .asFile
            tasks.withType<KotlinCompile>().configureEach {
                inputs.file(stability).withPathSensitivity(PathSensitivity.RELATIVE)
                compilerOptions.freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=${stability.absolutePath}",
                )
                if (reports) {
                    compilerOptions.freeCompilerArgs.addAll(
                        "-P",
                        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${reportsDir.absolutePath}",
                    )
                }
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
