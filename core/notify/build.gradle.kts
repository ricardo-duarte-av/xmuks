plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.notify"
}

// What the phone and the watch share for message notifications: opening gomuks' pushes, and
// showing them as conversations.
dependencies {
    api(projects.core.protocol)
    api(libs.coil)
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
}
