package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Walks a parent through locking the device down. Each step shows a tick once
 * it is done, and its button opens the right Android settings page.
 */
class SetupActivity : Activity() {

    private lateinit var settings: Settings
    private val refreshers = mutableListOf<() -> Unit>()

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        val rows = Rows(this, col)

        rows.title("Set up Toddler Launcher")
        rows.note(
            "Work down the list. A green tick means a step is done. You can come back here any time from " +
                "the parent area. The parent code starts as ${Settings.DEFAULT_PIN}."
        )

        refreshers += rows.step(
            "1. Make it the home screen",
            "Then the Home button always comes back to the toddler screen instead of your normal home screen.",
            "Set as home app",
            { Device.isDefaultHome(this) }
        ) {
            settings.startParentMode()
            Device.askToBeHome(this, 7)
        }

        refreshers += rows.step(
            "2. Switch on Toddler Guard",
            "Toddler Guard sends your child straight back if any other app, Settings, recent apps or the " +
                "notification shade opens. In the list, find Toddler Launcher (it may be under \"Downloaded apps\" " +
                "or \"Installed apps\"), open Toddler Guard and switch it on.",
            "Open accessibility settings",
            { Device.isGuardEnabled(this) }
        ) {
            settings.startParentMode()
            Device.openAccessibility(this)
        }
        rows.note(
            "Switch greyed out or \"Restricted setting\"? Android 13 and newer does this for apps installed from a " +
                "browser. Tap the button below, then the three dots (top right) > Allow restricted settings, " +
                "and try again."
        )
        rows.button("Open app info") {
            settings.startParentMode()
            Device.openAppInfo(this)
        }

        refreshers += rows.step(
            "3. Keep Toddler Guard awake",
            "Stops battery saving from switching Toddler Guard off in the background.",
            "Allow background use",
            { Device.isBatteryUnrestricted(this) }
        ) {
            settings.startParentMode()
            Device.askBatteryUnrestricted(this)
        }
        if (isXiaomi()) {
            rows.note("Xiaomi, Redmi and POCO phones also need Autostart switched on for Toddler Launcher.")
            rows.button("Open Autostart settings") {
                settings.startParentMode()
                val miui = Intent().setComponent(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                )
                if (!Device.open(this, miui)) Device.openAppInfo(this)
            }
        }

        refreshers += rows.step(
            "4. Allow app updates",
            "Lets the built-in updater install new versions from GitHub.",
            "Allow updates",
            { Device.canInstallUpdates(this) }
        ) {
            settings.startParentMode()
            Device.openInstallPermission(this)
        }

        refreshers += rows.step(
            "5. Change the parent code",
            "Replace ${Settings.DEFAULT_PIN} with your own PIN. You can switch to a pattern in the parent area.",
            "Change PIN",
            { !settings.isDefaultPin() }
        ) {
            Gate.changePin(this, settings) {
                if (!settings.hasRecoveryWord()) Gate.changeRecoveryWord(this, settings) { refresh() }
                refresh()
            }
        }

        refreshers += rows.step(
            "6. Choose the apps",
            "Only the apps you tick appear on the toddler screen.",
            "Choose apps",
            { settings.allowed.isNotEmpty() }
        ) {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        rows.header("Optional: screen pinning")
        rows.note(
            "Android's screen pinning locks the toddler screen in place, so even Home and Back do nothing. " +
                "Opening one of the allowed apps unpins it (Android only lets one app be pinned), and it pins " +
                "again when your child comes back. Android asks to confirm each time it pins, so this suits " +
                "a child who mostly uses the home screen itself. To use it, switch on Screen pinning (or App " +
                "pinning) in Android's security settings, and turn on \"Ask for PIN before unpinning\"."
        )
        rows.button("Open security settings") {
            settings.startParentMode()
            Device.openSecurity(this)
        }
        rows.switchRow("Pin the toddler screen", settings.pinScreen) { on ->
            settings.pinScreen = on
            on
        }

        rows.header("Finished?")
        rows.button("Go to the toddler screen") { finish() }

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        settings.startParentMode()
        refresh()
    }

    @Deprecated("Framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && !Device.isDefaultHome(this)) {
            AlertDialog.Builder(this)
                .setTitle("Not set yet")
                .setMessage("Open the settings page and choose Toddler Launcher as the home app?")
                .setPositiveButton("Open settings") { _, _ ->
                    settings.startParentMode()
                    Device.openHomeSettings(this)
                }
                .setNegativeButton("Not now", null)
                .show()
        }
        refresh()
    }

    private fun refresh() = refreshers.forEach { it() }

    private fun isXiaomi(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")
    }
}
