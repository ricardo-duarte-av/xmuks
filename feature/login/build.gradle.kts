plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.login"
}

dependencies {
    implementation(projects.core.data)
}
