plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.hilt)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.data"
}

dependencies {
    api(projects.core.network)
    api(projects.core.database)
    implementation(libs.androidx.datastore.preferences)
    api(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.zstd.jni) // desktop natives for the opt-in live test
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.test.ext.junit)
}
