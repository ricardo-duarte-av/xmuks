plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.android.compose)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.richtext"
    // JLaTeXMath finds its symbols and fonts by name.
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.jsoup)
    implementation(libs.jlatexmath.android)
    implementation(libs.androidx.core.ktx)
}
