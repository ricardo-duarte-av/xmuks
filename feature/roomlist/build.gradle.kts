plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.roomlist"
}

dependencies {
    implementation(projects.core.data)
}
