plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.telestream.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.telestream.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 16
        versionName = "0.7.0"
        // Where the app looks for a newer version: release/latest.json in this project's GitHub repository
        val updateUrl = (project.findProperty("updateUrl") as String?)
            ?: "https://raw.githubusercontent.com/TheYGN/telestream-tv/main/release/latest.json"
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    // One fixed key, so every new version installs over the old one. It is kept outside git
    // (TeleStreamTV/app/telestream.keystore in the project files); without it the build is signed with the debug key.
    val keystore = file("telestream.keystore")
    signingConfigs {
        create("telestream") {
            storeFile = keystore
            storePassword = "telestream"
            keyAlias = "telestream"
            keyPassword = "telestream"
        }
    }

    buildTypes {
        debug { if (keystore.exists()) signingConfig = signingConfigs.getByName("telestream") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore.exists()) "telestream" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api", "-opt-in=androidx.media3.common.util.UnstableApi")
    }
    lint {
        // opt-in is granted for the whole module in kotlinOptions; dispatchKeyEvent override is fine
        disable += setOf("UnsafeOptInUsageError", "RestrictedApi")
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
