plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.hilt)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.call"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

// MatrixRTC calls: signalling over gomuks, media over LiveKit, the call kept alive by a foreground
// service registered with Telecom.
dependencies {
    api(projects.core.data)
    implementation(projects.core.notify)
    implementation(libs.androidx.core.ktx)
    api(libs.livekit.android)
    implementation(libs.androidx.core.telecom)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
