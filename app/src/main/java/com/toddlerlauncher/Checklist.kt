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

    val CHECKS = listOf(
        Check("Home screen") { a, _ -> Device.isDefaultHome(a) },
        Check("Toddler Guard") { a, _ -> Device.isGuardEnabled(a) },
        Check("Background use") { a, _ -> Device.isBatteryUnrestricted(a) },
        Check("App updates allowed") { a, _ -> Device.canInstallUpdates(a) },
        Check("Own parent code") { _, s -> !s.isDefaultPin() || s.hasPattern() },
        Check("Apps chosen") { _, s -> s.allowed.isNotEmpty() },
    )

    /** Names of the checks that aren't done yet. */
    fun missing(a: android.content.Context, s: Settings): List<String> = CHECKS.filter { !it.isDone(a, s) }.map { it.title }

    /** Adds every step with its tick and button. Returns functions that refresh the ticks. */
    fun addSteps(a: Activity, rows: Rows, settings: Settings, onChanged: () -> Unit): List<() -> Unit> {
        val out = mutableListOf<() -> Unit>()
        fun done(i: Int): () -> Boolean = { CHECKS[i].isDone(a, settings) }

        out += rows.step(
            "1. Make it the home screen",
            "Then the Home button always comes back to the toddler screen instead of your normal home screen. " +
                "Android jumps to the toddler screen once it's set; this list opens again by itself.",
            "Set as home app",
            done(0)
        ) {
            settings.startParentMode()
            Device.askToBeHome(a, settings)
        }

        out += rows.step(
            "2. Switch on Toddler Guard",
            "Sends your child straight back if any other app, Settings, recent apps or the notification shade " +
                "opens. In the list, find Toddler Launcher (it may be under \"Downloaded apps\" or " +
                "\"Installed apps\"), open Toddler Guard and switch it on.",
            "Open accessibility settings",
            done(1)
        ) {
            settings.startParentMode()
            Device.openAccessibility(a)
        }
        rows.note(
            "Switch greyed out or \"Restricted setting\"? Android 13 and newer does this for apps installed from a " +
                "browser. Tap the button below, then the three dots (top right) > Allow restricted settings, " +
                "and try again."
        )
        rows.button("Open app info") {
            settings.startParentMode()
            Device.openAppInfo(a)
        }

        out += rows.step(
            "3. Keep Toddler Guard awake",
            "Stops battery saving from switching Toddler Guard off in the background. In the list, show all apps, pick Toddler Launcher and choose Don't optimise (or Unrestricted).",
            "Allow background use",
            done(2)
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

        out += rows.step(
            "4. Allow app updates",
            "Lets the built-in updater install new versions from GitHub.",
            "Allow updates",
            done(3)
        ) {
            settings.startParentMode()
            Device.openInstallPermission(a)
        }

        out += rows.step(
            "5. Change the parent code",
            "Replace ${Settings.DEFAULT_PIN} with your own PIN (or set a pattern in the parent area).",
            "Change PIN",
            done(4)
        ) {
            Gate.changePin(a, settings) {
                if (!settings.hasRecoveryWord()) Gate.changeRecoveryWord(a, settings) { onChanged() }
                onChanged()
            }
        }

        out += rows.step(
            "6. Choose the apps",
            "Only the apps you tick appear on the toddler screen.",
            "Choose apps",
            done(5)
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
