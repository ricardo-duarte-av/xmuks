plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.room"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.jsoup)
    implementation(libs.androidx.activity.compose)
}
