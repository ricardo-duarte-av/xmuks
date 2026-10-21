plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.profile"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.richtext)
    implementation(projects.core.notify)
    implementation(libs.androidx.activity.compose)
}
