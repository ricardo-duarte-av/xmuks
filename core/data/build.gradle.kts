plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.hilt)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.data"
}

dependencies {
    api(projects.core.network)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
}
