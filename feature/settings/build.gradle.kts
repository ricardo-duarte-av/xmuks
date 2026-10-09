plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.settings"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.activity.compose)
}
