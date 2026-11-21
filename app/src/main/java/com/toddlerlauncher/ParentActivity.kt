package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The parent area. Every change is saved and applied straight away. */
class ParentActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var scroll: ScrollView
    private lateinit var col: LinearLayout

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        scroll = ScrollView(this).apply { addView(col) }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        settings.startParentMode()
        build()
    }

    private fun build() {
        val y = scroll.scrollY
        col.removeAllViews()
        val rows = Rows(this, col)

        rows.title("Parent area")
        rows.button("Back to the toddler screen") { finish() }

        // ---------- Apps ----------
        rows.header("Apps")
        rows.button("Choose allowed apps (${settings.allowed.size} chosen)") {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }

        // ---------- Layout ----------
        rows.header("Home screen layout")
        rows.choiceRow("Columns (across)", Settings.GRID_SIZES, settings.columns.toString()) {
            settings.columns = it.toInt()
        }
        rows.choiceRow("Rows (down)", Settings.GRID_SIZES, settings.rows.toString()) { settings.rows = it.toInt() }
        rows.note("If there are more apps than fit, they go onto extra pages with arrows.")
        rows.choiceRow("Icon size", Settings.ICON_SIZES, settings.iconSize) { settings.iconSize = it }
        rows.switchRow("Show app names", settings.showNames) { on -> settings.showNames = on; on }
        rows.choiceRow("Screen direction", Settings.ORIENTATIONS, settings.orientation) { settings.orientation = it }
        rows.switchRow("Hide the status and navigation bars", settings.hideBars) { on -> settings.hideBars = on; on }
        rows.note("Makes the notification shade harder to reach by accident.")
        rows.switchRow("Keep the screen on", settings.keepScreenOn) { on -> settings.keepScreenOn = on; on }

        // ---------- Wallpaper ----------
        rows.header("Wallpaper")
        val walls = Settings.WALLPAPERS.map { it.first to it.second.first } + ("photo" to "My photo")
        rows.choiceRow("Background", walls, settings.wallpaper) {
            if (it == "photo" && !File(filesDir, MainActivity.WALLPAPER_FILE).exists()) pickPhoto()
            else settings.wallpaper = it
        }
        rows.button("Choose a photo") { pickPhoto() }
        rows.switchRow("Darken the photo a little", settings.dimPhoto) { on -> settings.dimPhoto = on; on }
        rows.note("Helps the app names stand out on a bright photo.")

        // ---------- Parent button ----------
        rows.header("Parent button")
        rows.choiceRow("Corner", Settings.CORNERS, settings.gearCorner) { settings.gearCorner = it }
        rows.choiceRow("Hold for", Settings.HOLD_TIMES, settings.holdSeconds.toString()) {
            settings.holdSeconds = it.toInt()
        }
        rows.switchRow("Show the update button on the toddler screen", settings.showUpdateOnHome) { on ->
            settings.showUpdateOnHome = on
            on
        }
        rows.note("Handy while the app is being built. Switch it off once things settle down.")

        // ---------- Unlock ----------
        rows.header("Parent code")
        rows.note(
            when {
                settings.isDefaultPin() -> "Still using the default PIN ${Settings.DEFAULT_PIN}. Please change it."
                else -> "Your own PIN is set."
            } + if (settings.hasPattern()) " A pattern is set." else ""
        )
        rows.choiceRow("Open the parent area with", Settings.LOCK_TYPES, settings.lockType) {
            if (it == "pattern" && !settings.hasPattern()) Gate.changePattern(this, settings) { build() }
            else settings.lockType = it
        }
        rows.button("Change PIN") { Gate.changePin(this, settings) { build() } }
        rows.button(if (settings.hasPattern()) "Change pattern" else "Set a pattern") {
            Gate.changePattern(this, settings) { build() }
        }
        rows.button(if (settings.hasRecoveryWord()) "Change recovery word" else "Set a recovery word") {
            Gate.changeRecoveryWord(this, settings) { build() }
        }
        rows.note("The recovery word resets a forgotten PIN or pattern (\"Forgot?\" on the PIN box).")

        // ---------- Protection ----------
        rows.header("Protection")
        val home = Device.isDefaultHome(this)
        val guard = Device.isGuardEnabled(this)
        rows.note(
            (if (home) "Toddler Launcher is the home app. " else "Toddler Launcher is NOT the home app yet. ") +
                (if (guard) "Toddler Guard is switched on in Android." else "Toddler Guard is NOT switched on in Android.")
        )
        rows.button("Setup and lock-down checklist") { startActivity(Intent(this, SetupActivity::class.java)) }
        rows.switchRow("Use Toddler Guard", settings.guardOn) { on -> settings.guardOn = on; on }
        rows.note("Off = the guard stays installed but lets everything through.")
        if (settings.isPaused()) {
            val until = if (settings.pausedUntil == Long.MAX_VALUE) "until you switch it back on"
            else "until " + SimpleDateFormat("HH:mm", Locale.UK).format(Date(settings.pausedUntil))
            rows.note("Protection is paused $until.")
            rows.button("Switch protection back on") {
                settings.pausedUntil = 0L
                build()
            }
        } else {
            rows.button("Pause protection...") { askPause() }
        }
        rows.note("Pausing lets you use the device normally. The toddler screen shows an orange reminder.")
        rows.switchRow("Pin the toddler screen (screen pinning)", settings.pinScreen) { on ->
            settings.pinScreen = on
            on
        }

        // ---------- Updates ----------
        rows.header("App updates")
        rows.note("You have version ${Updater.installedVersion(this)}.")
        rows.button("Check for updates or choose a version") { Updater.showDialog(this, settings) }

        // ---------- Backup ----------
        rows.header("Backup")
        rows.button("Save settings to a file") { saveBackup() }
        rows.button("Restore settings from a file") { openBackup() }
        rows.note("Includes allowed apps, layout and your PIN or pattern. A wallpaper photo isn't included.")

        // ---------- Device ----------
        rows.header("This device")
        rows.button("Change the home app (back to your normal home screen)") {
            settings.startParentMode()
            Device.openHomeSettings(this)
        }
        rows.note("Pick your normal home app here when you want the device back. Pick Toddler Launcher to return.")
        rows.button("Uninstall Toddler Launcher") {
            settings.startParentMode()
            Device.open(this, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }

        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun askPause() {
        val options = arrayOf("15 minutes", "1 hour", "Until I switch it back on")
        AlertDialog.Builder(this)
            .setTitle("Pause protection for")
            .setItems(options) { _, which ->
                val now = System.currentTimeMillis()
                settings.pausedUntil = when (which) {
                    0 -> now + 15 * 60_000L
                    1 -> now + 60 * 60_000L
                    else -> Long.MAX_VALUE
                }
                build()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Files: wallpaper photo and backups ----------

    private fun pickPhoto() {
        settings.startParentMode()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try {
            startActivityForResult(i, REQ_PHOTO)
        } catch (e: Exception) {
            Ui.toast(this, "No app found to pick a photo")
        }
    }

    private fun saveBackup() {
        settings.startParentMode()
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, "toddler-launcher-settings.json")
        try {
            startActivityForResult(i, REQ_SAVE)
        } catch (e: Exception) {
            Ui.toast(this, "No app found to save files")
        }
    }

    private fun openBackup() {
        settings.startParentMode()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        try {
            startActivityForResult(i, REQ_OPEN)
        } catch (e: Exception) {
            Ui.toast(this, "No app found to open files")
        }
    }

    @Deprecated("Framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            when (requestCode) {
                REQ_PHOTO -> {
                    contentResolver.openInputStream(uri)!!.use { input ->
                        File(filesDir, MainActivity.WALLPAPER_FILE).outputStream().use { input.copyTo(it) }
                    }
                    settings.wallpaper = "photo"
                    Ui.toast(this, "Wallpaper set")
                }
                REQ_SAVE -> {
                    contentResolver.openOutputStream(uri, "wt")!!.use { it.write(settings.exportJson().toByteArray()) }
                    Ui.toast(this, "Settings saved")
                }
                REQ_OPEN -> {
                    val text = contentResolver.openInputStream(uri)!!.use { it.bufferedReader().readText() }
                    if (settings.importJson(text)) Ui.toast(this, "Settings restored")
                    else Ui.toast(this, "That isn't a Toddler Launcher settings file")
                }
            }
        } catch (e: Exception) {
            Ui.toast(this, "Something went wrong: ${e.message}")
        }
        build()
    }

    companion object {
        private const val REQ_PHOTO = 31
        private const val REQ_SAVE = 32
        private const val REQ_OPEN = 33
    }
}
