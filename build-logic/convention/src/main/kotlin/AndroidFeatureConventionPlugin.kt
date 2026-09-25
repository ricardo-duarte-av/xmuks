import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/** `feature:*` modules: Android library + Compose + Hilt + screenshots, design system pre-wired. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("xmuks.android.library")
            pluginManager.apply("xmuks.android.compose")
            pluginManager.apply("xmuks.hilt")
            pluginManager.apply("xmuks.screenshots")

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            }
        }
    }
}
