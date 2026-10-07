plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.android.compose)
    alias(libs.plugins.xmuks.screenshots)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.animation)
    implementation(libs.material.kolor)
    implementation(projects.core.protocol)
    api(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
}
