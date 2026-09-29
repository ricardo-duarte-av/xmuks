import java.util.Properties

plugins {
    alias(libs.plugins.xmuks.android.application)
    alias(libs.plugins.xmuks.android.compose)
    alias(libs.plugins.xmuks.hilt)
    alias(libs.plugins.xmuks.screenshots)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Release signing: keystore.properties (local, gitignored) first, then environment variables (CI).
// With neither, the release build is produced unsigned instead of failing.
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun signingValue(
    propKey: String,
    envKey: String,
): String? = keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { it != null }

// Google Maps key (location picker and map previews): secrets.properties (local, gitignored), then
// the MAPS_API_KEY environment variable (CI). Without it maps just don't load; nothing else breaks.
val secrets =
    Properties().apply {
        val file = rootProject.file("secrets.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
val mapsApiKey = secrets.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY").orEmpty()

android {
    namespace = "pt.aguiarvieira.xmuks"

    defaultConfig {
        applicationId = "pt.aguiarvieira.xmuks"
        // CI's verify-tag job checks that a `vX.Y.Z` tag matches versionName.
        versionCode = 20
        versionName = "0.0.20"
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        // No applicationIdSuffix on debug: google-services.json only registers the base package.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.feature.login)
    implementation(projects.feature.roomlist)
    implementation(projects.feature.room)
    implementation(projects.feature.media)
    implementation(projects.feature.profile)
    implementation(projects.feature.settings)
    implementation(projects.core.push)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.kotlinx.serialization.json)
}
