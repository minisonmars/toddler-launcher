plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Toddler Parent: the app for the parent's phone. It finds Toddler Launcher on the
// same Wi-Fi (or joins its private hotspot), pairs once with the parent PIN, and
// changes its settings. Built and released together with the launcher.
android {
    namespace = "com.toddlerlauncher.parent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.toddlerlauncher.parent"
        minSdk = 29 // Android 10: needed to join the launcher's private hotspot
        targetSdk = 34
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("APP_VERSION") ?: "0.1.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets {
        getByName("main").java.srcDir("../shared/java")
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
    // Reading QR codes from the camera
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
}
