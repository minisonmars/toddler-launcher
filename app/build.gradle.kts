plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.toddlerlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.toddlerlauncher"
        minSdk = 26 // Android 8.0 and newer
        targetSdk = 34
        // Goes up with every GitHub build so each APK counts as an update
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION") ?: "0.1.0"
    }

    // A fixed key kept in the repo, so every build is signed the same way and
    // installs over the previous one. (Debug use only, not for the Play Store.)
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Builds are "release" builds (not debuggable), signed with the same fixed key so
    // they still install over earlier versions. Debuggable apps that use accessibility
    // look more suspicious to Google Play Protect.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Makes the QR code for remote access (works offline, nothing is sent anywhere)
    implementation("com.google.zxing:core:3.5.3")
}
