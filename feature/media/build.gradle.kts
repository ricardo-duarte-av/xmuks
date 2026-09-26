plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.media"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)
}
