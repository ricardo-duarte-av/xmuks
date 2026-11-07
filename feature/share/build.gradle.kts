plugins {
    alias(libs.plugins.xmuks.android.feature)
}

android {
    namespace = "pt.aguiarvieira.xmuks.feature.share"
}

dependencies {
    implementation(projects.core.data)
    // The composer's @people, #rooms and :emoji: completion, for the captions.
    implementation(projects.feature.room)
    implementation(libs.androidx.activity.compose)
}
