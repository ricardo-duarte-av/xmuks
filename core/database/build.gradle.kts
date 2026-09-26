plugins {
    alias(libs.plugins.xmuks.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "pt.aguiarvieira.xmuks.core.database"
}

dependencies {
    api(libs.androidx.room3.runtime)
    api(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room3.compiler)
    // Production driver: a current SQLite compiled into the app, identical on every Android version.
    implementation(libs.androidx.sqlite.bundled)
    // The bundled driver's native library can't load in JVM unit tests; tests use the framework driver
    // on Robolectric's SQLite instead. Same schema and SQL either way.
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
