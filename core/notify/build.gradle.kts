plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.notify"
}

// What the phone and the watch share for message notifications: opening gomuks' pushes, showing
// them as conversations, and caching gomuks media (avatars, pictures) by what it is.
dependencies {
    api(projects.core.protocol)
    api(libs.coil)
    api(libs.coil.network.okhttp)
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
