import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Shared Android + Kotlin configuration applied to every Android module. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.int("compileSdk")
        compileSdkMinor = libs.int("compileSdkMinor")
        defaultConfig.minSdk = libs.int("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlinCompile()
}

/**
 * JVM target plus the CI-only warnings gate: `-PwarningsAsErrors=true` turns every Kotlin
 * warning into an error. Local builds stay lenient so a dependency bump that deprecates an API
 * doesn't make the project unbuildable mid-upgrade.
 */
internal fun Project.configureKotlinCompile() {
    val warningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(warningsAsErrors)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}
