package com.toddlerlauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build

/**
 * The lock-down checks, shared by the setup screen and the parent area, so a
 * parent can always see if something has been switched off since setup.
 */
object Checklist {

    class Check(val title: String, val isDone: (android.content.Context, Settings) -> Boolean)

    private const val UPDATES = "App updates allowed"

    val CHECKS = listOfNotNull(
        Check("Home screen") { a, _ -> Device.isDefaultHome(a) },
        Check("Toddler Guard") { a, _ -> Device.isGuardEnabled(a) },
        Check("Background use") { a, _ -> Device.isBatteryUnrestricted(a) },
        // The Play version is updated by Google Play, so it doesn't need this
        if (Store.PLAY) null else Check(UPDATES) { a, _ -> Device.canInstallUpdates(a) },
        Check("Own parent code") { _, s -> !s.isDefaultPin() || s.hasPattern() },
        Check("Apps chosen") { _, s -> s.allowed.isNotEmpty() },
    )

    /** Names of the checks that aren't done yet. */
    fun missing(a: android.content.Context, s: Settings): List<String> = CHECKS.filter { !it.isDone(a, s) }.map { it.title }

    /** Adds every step with its tick and button. Returns functions that refresh the ticks. */
    fun addSteps(a: Activity, rows: Rows, settings: Settings, onChanged: () -> Unit): List<() -> Unit> {
        val out = mutableListOf<() -> Unit>()
        fun done(title: String): () -> Boolean = { CHECKS.first { it.title == title }.isDone(a, settings) }
        var n = 0
        fun num() = "${++n}. "

        out += rows.step(
            num() + "Make it the home screen",
            "Then the Home button always comes back to the toddler screen instead of your normal home screen. " +
                "Android jumps to the toddler screen once it's set; this list opens again by itself.",
            "Set as home app",
            done("Home screen")
        ) {
            settings.startParentMode()
            Device.askToBeHome(a, settings)
        }

        out += rows.step(
            num() + "Switch on Toddler Guard",
            "Sends your child straight back if any other app, Settings, recent apps or the notification shade " +
                "opens. In the list, find Toddler Launcher (it may be under \"Downloaded apps\" or " +
                "\"Installed apps\"), open Toddler Guard and switch it on.",
            "Open accessibility settings",
            done("Toddler Guard")
        ) {
            // Google Play asks for a clear explanation and an "Agree" before accessibility settings open
            Disclosure.accessibility(a) {
                settings.startParentMode()
                Device.openAccessibility(a)
            }
        }
        // Only apps installed from a browser get "Restricted setting", not ones from Google Play
        if (!Store.PLAY) {
            rows.note(
                "Switch greyed out or \"Restricted setting\"? Android 13 and newer does this for apps installed from a " +
                    "browser. Tap the button below, then the three dots (top right) > Allow restricted settings, " +
                    "and try again."
            )
            rows.button("Open app info") {
                settings.startParentMode()
                Device.openAppInfo(a)
            }
        }

        out += rows.step(
            num() + "Keep Toddler Guard awake",
            "Stops battery saving from switching Toddler Guard off in the background. In the list, show all apps, pick Toddler Launcher and choose Don't optimise (or Unrestricted).",
            "Allow background use",
            done("Background use")
        ) {
            settings.startParentMode()
            Device.askBatteryUnrestricted(a)
        }
        if (isXiaomi()) {
            rows.note("Xiaomi, Redmi and POCO phones also need Autostart switched on for Toddler Launcher. " +
                "Android can't tell the app whether it's on, so there's no tick for this one.")
            rows.button("Open Autostart settings") {
                settings.startParentMode()
                val miui = Intent().setComponent(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                )
                if (!Device.open(a, miui)) Device.openAppInfo(a)
            }
        }

        if (!Store.PLAY) {
            out += rows.step(
                num() + "Allow app updates",
                "Lets the built-in updater install new versions from GitHub.",
                "Allow updates",
                done(UPDATES)
            ) {
                settings.startParentMode()
                Device.openInstallPermission(a)
            }
        }

        out += rows.step(
            num() + "Change the parent code",
            "Replace ${Settings.DEFAULT_PIN} with your own PIN (or set a pattern in the parent area).",
            "Change PIN",
            done("Own parent code")
        ) {
            Gate.changePin(a, settings) {
                if (!settings.hasRecoveryWord()) Gate.changeRecoveryWord(a, settings) { onChanged() }
                onChanged()
            }
        }

        out += rows.step(
            num() + "Choose the apps",
            "Only the apps you tick appear on the toddler screen.",
            "Choose apps",
            done("Apps chosen")
        ) {
            a.startActivity(Intent(a, AppPickerActivity::class.java))
        }
        return out
    }

    private fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }
}
