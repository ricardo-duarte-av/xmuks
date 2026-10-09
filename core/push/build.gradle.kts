plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.xmuks.android.compose)
    alias(libs.plugins.xmuks.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.push"
}

dependencies {
    api(projects.core.notify)
    implementation(projects.core.data)
    implementation(projects.core.call)
    implementation(projects.core.designsystem)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
}

// Its tests moved to core:notify with the code they cover; Hilt still generates unit-test sources here.
tasks.withType<Test>().configureEach { failOnNoDiscoveredTests = false }
