// Plugins are declared here (apply = false) so module scripts and convention plugins can apply
// them without re-declaring versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

// One detekt run over every module's sources (no type resolution needed for our rule set).
detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/*/kotlin/**/*.kt", "**/src/*/java/**/*.kt")
            exclude("**/build/**", "build-logic/**")
        },
    )
}

dependencies {
    detektPlugins(libs.compose.rules.detekt)
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
