plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.room"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.richtext)
    implementation(libs.androidx.activity.compose)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
}
