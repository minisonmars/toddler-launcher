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
        // Upload key for Google Play. It is never in the repo: the build writes upload.keystore
        // from the PLAY_UPLOAD_KEYSTORE secret, with PLAY_KEY_PASSWORD as its password. Google
        // re-signs the app with its own key (Play App Signing), so this only proves uploads are ours.
        create("upload") {
            storeFile = file("upload.keystore")
            storePassword = System.getenv("PLAY_KEY_PASSWORD") ?: ""
            keyAlias = "upload"
            keyPassword = System.getenv("PLAY_KEY_PASSWORD") ?: ""
        }
    }

    // Two versions from the same code:
    //  - github: the APK on GitHub Releases, with the built-in updater and recommended apps
    //  - play:   the Google Play version (AAB), updated by Play, with no self-updating at all
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            applicationId = "com.toddlerlauncher"
            signingConfig = signingConfigs.getByName("debug")
        }
        create("play") {
            dimension = "store"
            // Can never change once the app is on Play
            applicationId = "uk.co.minisonmars.toddlerlauncher"
            signingConfig = signingConfigs.getByName("upload")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Builds are "release" builds (not debuggable), signed with the same fixed key so
    // they still install over earlier versions. Debuggable apps that use accessibility
    // look more suspicious to Google Play Protect.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // Signing is set per version above: github uses the fixed key, play the upload key
        }
    }

    // Code shared with the Toddler Parent app (pairing and locked messages)
    sourceSets {
        getByName("main").java.srcDir("../shared/java")
        // The Play version shows CHANGELOG.md in the parent area
        getByName("play").assets.srcDir(layout.buildDirectory.dir("generated/changelog"))
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Copies CHANGELOG.md into the Play version, so "What's new" works without the internet
val copyChangelog by tasks.registering(Copy::class) {
    from(rootProject.file("CHANGELOG.md"))
    into(layout.buildDirectory.dir("generated/changelog"))
}
tasks.named("preBuild") { dependsOn(copyChangelog) }

dependencies {
    // Play version only: asks Google Play whether a newer version is out and shows Play's update screen
    "playImplementation"("com.google.android.play:app-update:2.1.0")
    // Makes the QR code for remote access (works offline, nothing is sent anywhere)
    implementation("com.google.zxing:core:3.5.3")
}
