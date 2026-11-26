package com.toddlerlauncher

/** Which version this is: the GitHub APK or the Google Play version (see productFlavors in build.gradle.kts). */
object Store {
    val PLAY = BuildConfig.FLAVOR == "play"

    /** The parent area sections this version has. The Play version can't install apps from GitHub. */
    val sections get() = Sections.ALL.filter { it.key != "checks" && !(PLAY && it.key == "recommended") }
}
