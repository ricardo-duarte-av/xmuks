plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.account"
}

dependencies {
    api(projects.core.network)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
}
