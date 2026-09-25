import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "pt.aguiarvieira.xmuks.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    // Applied by id only (no API references): kept off the compile classpath so their Kotlin
    // metadata can't skew against Gradle's embedded compiler.
    runtimeOnly(libs.compose.gradlePlugin)
    runtimeOnly(libs.ksp.gradlePlugin)
    runtimeOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "xmuks.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "xmuks.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "xmuks.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "xmuks.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("jvmLibrary") {
            id = "xmuks.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = "xmuks.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("screenshots") {
            id = "xmuks.screenshots"
            implementationClass = "ScreenshotConventionPlugin"
        }
    }
}
