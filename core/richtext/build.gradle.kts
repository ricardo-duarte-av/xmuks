plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.android.compose)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.richtext"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.jsoup)
    implementation(libs.androidx.core.ktx)
}
