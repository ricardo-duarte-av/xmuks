plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.share"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.activity.compose)
}
