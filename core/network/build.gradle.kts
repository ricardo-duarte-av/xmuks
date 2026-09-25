plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.network"
}

dependencies {
    api(projects.core.protocol)
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    // The .aar carries the Android native libraries; JVM unit tests use the plain jar (desktop natives).
    implementation(libs.zstd.jni) { artifact { type = "aar" } }
    testImplementation(libs.zstd.jni)
    testImplementation(libs.okhttp.mockwebserver)
}
