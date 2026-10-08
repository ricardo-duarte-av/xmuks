plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.call"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.call)
    implementation(libs.androidx.activity.compose)
}
