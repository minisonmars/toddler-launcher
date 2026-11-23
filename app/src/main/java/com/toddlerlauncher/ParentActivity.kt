package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    private val section get() = intent.getStringExtra(SECTION) ?: ""

    private fun open(section: String) {
        startActivity(Intent(this, ParentActivity::class.java).putExtra(SECTION, section))
    }

    private fun build() {
        val y = scroll.scrollY
        col.removeAllViews()
        val rows = Rows(this, col)
        when (section) {
            "apps" -> buildApps(rows)
            "time" -> buildTime(rows)
            "report" -> buildReport(rows)
            "look" -> buildLook(rows)
            "code" -> buildCode(rows)
            "protect" -> buildProtect(rows)
            "connect" -> buildConnect(rows)
            "updates" -> buildUpdates(rows)
            "device" -> buildDevice(rows)
            "recommended" -> buildRecommended(rows)
            "remote" -> buildRemote(rows)
            else -> buildMenu(rows)
        }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun sectionTitle(rows: Rows, text: String) {
        rows.title(text)
        rows.button("Back") { finish() }
    }

    // ---------- The main menu ----------

    private fun buildMenu(rows: Rows) {
        rows.title("Parent area")
        rows.button("Back to the toddler screen") { closeParentArea() }

        val missing = Checklist.missing(this, settings)
        rows.menuItem(
            "Lock-down checks",
            if (missing.isEmpty()) "Everything is set up" else "Needs attention: " + missing.joinToString(", "),
            warn = missing.isNotEmpty()
        ) { startActivity(Intent(this, SetupActivity::class.java)) }

        rows.menuItem("Apps and app time limits", "${settings.allowed.size} apps allowed") { open("apps") }
        rows.menuItem("Recommended apps", "Install other toddler apps made for this launcher") { open("recommended") }

        val u = Usage(this)
        val limit = if (settings.dailyLimitMin > 0) " of " + Usage.format(Rules.dailyAllowanceSec(settings)) else ""
        rows.menuItem(
            "Screen time and allowed hours",
            "Today: " + Usage.format(u.appSeconds(LocalDate.now())) + limit +
                if (settings.hoursOn) ". Allowed hours on" else ""
        ) { open("time") }
        rows.menuItem("Usage report", "The last 3 days and a 7-day average") { open("report") }
        rows.menuItem("Look and layout", "Grid, icons, wallpaper, screen direction") { open("look") }
        rows.menuItem(
            "Parent code and button",
            if (settings.isDefaultPin() && !settings.hasPattern()) "Still using the default PIN ${Settings.DEFAULT_PIN}"
            else "PIN, pattern, recovery word, gear button",
            warn = settings.isDefaultPin() && !settings.hasPattern()
        ) { open("code") }
        rows.menuItem(
            "Protection and volume",
            if (settings.isPaused()) "Protection is PAUSED" else "Toddler Guard, pull-down block, volume cap, pinning",
            warn = settings.isPaused()
        ) { open("protect") }
        rows.menuItem("Wi-Fi, mobile data and Bluetooth", "Quick switches for you") { open("connect") }
        rows.menuItem(
            "Remote access",
            if (settings.remoteOn) "On: change settings from your phone's browser" else "Off",
        ) { open("remote") }
        rows.menuItem("Updates and backup", "You have version ${Updater.installedVersion(this)}") { open("updates") }
        rows.menuItem("This device", "Use your normal home screen, uninstall") { open("device") }
    }

    private fun closeParentArea() {
        // Back to the toddler screen, closing every parent screen on the way
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    // ---------- Apps ----------

    private fun buildApps(rows: Rows) {
        sectionTitle(rows, "Apps")
        rows.button("Choose allowed apps (${settings.allowed.size} chosen)") {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        rows.header("Daily time limit for each app")
        rows.note("Once an app's time is used up, it shows faded on the toddler screen until tomorrow.")
        val u = Usage(this)
        val list = Apps.allowed(this, settings)
        if (list.isEmpty()) rows.note("Choose some apps first.")
        for (app in list) {
            val used = Usage.format(u.seconds(LocalDate.now(), app.pkg))
            rows.choiceRow("${app.label} (today: $used)", Settings.APP_LIMITS, settings.appLimitMin(app.pkg).toString()) {
                settings.setAppLimit(app.pkg, it.toInt())
            }
        }
    }

    // ---------- Screen time ----------

    private fun buildTime(rows: Rows) {
        sectionTitle(rows, "Screen time")
        val u = Usage(this)
        if (!Device.isGuardEnabled(this)) {
            rows.note("Toddler Guard is off in Android, so screen time can't be counted or limited. " +
                "Switch it on from Lock-down checks.").setTextColor(Ui.RED)
        }
        rows.header("Daily limit")
        rows.note("Counts time spent inside the apps, not on the toddler home screen.")
        rows.choiceRow("Total time in apps per day", Settings.DAILY_LIMITS, settings.dailyLimitMin.toString()) {
            settings.dailyLimitMin = it.toInt()
            col.post { build() }
        }
        val used = u.appSeconds(LocalDate.now())
        rows.note(
            "Used today: " + Usage.format(used) +
                (if (settings.dailyLimitMin > 0) ", allowed " + Usage.format(Rules.dailyAllowanceSec(settings)) else "") +
                (if (settings.bonusToday() > 0) " (including ${settings.bonusToday()} min extra)" else "")
        )
        rows.button("Give 15 more minutes today") {
            settings.addBonus(15)
            Ui.toast(this, "15 extra minutes added for today")
            build()
        }
        rows.note("Extra time is added to the daily limit and to each app's limit, for today only.")
        rows.switchRow("\"5 minutes left\" warning", settings.warnings) { on -> settings.warnings = on; on }

        rows.header("Allowed hours")
        rows.note("Outside these times the toddler screen shows \"Sleep time\" and no apps open.")
        rows.switchRow("Only allow use between set times", settings.hoursOn) { on ->
            settings.hoursOn = on
            col.post { build() }
            on
        }
        if (settings.hoursOn) {
            timeButton(rows, "Monday to Friday from", settings.weekdayStart) { settings.weekdayStart = it }
            timeButton(rows, "Monday to Friday until", settings.weekdayEnd) { settings.weekdayEnd = it }
            timeButton(rows, "Saturday and Sunday from", settings.weekendStart) { settings.weekendStart = it }
            timeButton(rows, "Saturday and Sunday until", settings.weekendEnd) { settings.weekendEnd = it }
        }
    }

    private fun timeButton(rows: Rows, label: String, minutes: Int, onSet: (Int) -> Unit) {
        rows.button("$label  ${Rules.hhmm(minutes)}") {
            TimePickerDialog(this, { _, h, m ->
                onSet(h * 60 + m)
                build()
            }, minutes / 60, minutes % 60, true).show()
        }
    }

    // ---------- Usage report ----------

    private fun buildReport(rows: Rows) {
        sectionTitle(rows, "Usage report")
        rows.note("Time on screen in toddler mode. Time while you're in the parent area or protection is paused isn't counted.")
        val u = Usage(this)
        val today = LocalDate.now()
        val days = (0..2).map { today.minusDays(it.toLong()) }
        val week = (0..6).map { today.minusDays(it.toLong()) }
        val dayName = DateTimeFormatter.ofPattern("EEE", Locale.UK)

        val pkgs = week.flatMap { u.packages(it) }.toSet()
        val names = HashMap<String, String>()
        for (pkg in pkgs) {
            names[pkg] = if (pkg == packageName) "Home screen" else try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
        }

        reportRow(listOf("App", "Today", "Yest.", dayName.format(days[2]), "7-day avg"), bold = true)
        val appPkgs = pkgs.filter { it != packageName }
            .sortedByDescending { p -> week.sumOf { u.seconds(it, p) } }
        for (pkg in appPkgs) {
            reportRow(listOf(names[pkg] ?: pkg) + days.map { short(u.seconds(it, pkg)) } +
                short(week.sumOf { u.seconds(it, pkg) } / 7))
        }
        reportRow(listOf("All apps") + days.map { short(u.appSeconds(it)) } + short(week.sumOf { u.appSeconds(it) } / 7), bold = true)
        if (pkgs.contains(packageName)) {
            reportRow(listOf("Home screen") + days.map { short(u.seconds(it, packageName)) } +
                short(week.sumOf { u.seconds(it, packageName) } / 7))
        }
        if (pkgs.isEmpty()) rows.note("Nothing recorded yet. Counting needs Toddler Guard switched on.")
        rows.note("The 7-day average is the total for the last 7 days (including today) divided by 7.")
        rows.button("Clear the usage history") {
            AlertDialog.Builder(this).setTitle("Clear usage history?")
                .setMessage("This also resets today's screen time.")
                .setPositiveButton("Clear") { _, _ -> u.clearAll(); build() }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun short(secs: Long): String {
        val m = (secs + 30) / 60
        return if (m < 60) "${m}m" else String.format(Locale.UK, "%dh%02d", m / 60, m % 60)
    }

    private fun reportRow(cells: List<String>, bold: Boolean = false) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        cells.forEachIndexed { i, text ->
            row.addView(TextView(this).apply {
                this.text = text
                textSize = 14f
                if (bold) typeface = Typeface.DEFAULT_BOLD
                gravity = if (i == 0) Gravity.START else Gravity.END
                maxLines = 2
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, if (i == 0) 2.2f else 1f))
        }
        col.addView(row)
    }

    // ---------- Look and layout ----------

    private fun buildLook(rows: Rows) {
        sectionTitle(rows, "Look and layout")
        rows.header("Grid")
        rows.choiceRow("Columns (across)", Settings.GRID_SIZES, settings.columns.toString()) {
            settings.columns = it.toInt()
        }
        rows.choiceRow("Rows (down)", Settings.GRID_SIZES, settings.rows.toString()) { settings.rows = it.toInt() }
        rows.note("If there are more apps than fit, they go onto extra pages with arrows.")
        rows.switchRow("Swap rows and columns when sideways", settings.swapWhenSideways) { on ->
            settings.swapWhenSideways = on
            on
        }
        rows.note("Set the grid for the device held upright. Turned sideways, 5 rows of 3 become 3 rows of 5.")
        rows.header("Icons")
        rows.choiceRow("Icon size", Settings.ICON_SIZES, settings.iconSize) { settings.iconSize = it }
        rows.switchRow("Show app names", settings.showNames) { on -> settings.showNames = on; on }

        rows.header("Wallpaper")
        val walls = Settings.WALLPAPERS.map { it.first to it.second.first } + ("photo" to "My photo")
        rows.choiceRow("Background", walls, settings.wallpaper) {
            if (it == "photo" && !File(filesDir, MainActivity.WALLPAPER_FILE).exists()) pickPhoto()
            else settings.wallpaper = it
        }
        rows.button("Choose a photo") { pickPhoto() }
        rows.switchRow("Darken the photo a little", settings.dimPhoto) { on -> settings.dimPhoto = on; on }
        rows.note("Helps the app names stand out on a bright photo.")

        rows.header("Screen")
        rows.choiceRow("Screen direction", Settings.ORIENTATIONS, settings.orientation) { settings.orientation = it }
        rows.switchRow("Hide the status and navigation bars", settings.hideBars) { on -> settings.hideBars = on; on }
        rows.switchRow("Keep the screen on", settings.keepScreenOn) { on -> settings.keepScreenOn = on; on }
    }

    // ---------- Parent code and button ----------

    private fun buildCode(rows: Rows) {
        sectionTitle(rows, "Parent code and button")
        rows.header("Code")
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
    }

    // ---------- Protection ----------

    private fun buildProtect(rows: Rows) {
        sectionTitle(rows, "Protection and volume")
        rows.header("Toddler Guard")
        rows.switchRow("Use Toddler Guard", settings.guardOn) { on -> settings.guardOn = on; on }
        rows.note("Off = the guard stays installed but lets everything through, and screen-time limits stop.")
        if (settings.isPaused()) {
            val until = if (settings.pausedUntil == Long.MAX_VALUE) "until you switch it back on"
            else "until " + SimpleDateFormat("HH:mm", Locale.UK).format(Date(settings.pausedUntil))
            rows.note("Protection is paused $until.").setTextColor(Ui.RED)
            rows.button("Switch protection back on") {
                settings.stopOtherHome()
                build()
            }
        } else {
            rows.button("Pause protection...") { askPause() }
        }
        rows.note("Pausing lets you use the device normally. The toddler screen shows an orange reminder.")
        rows.switchRow("Block pulling down notifications and quick settings", settings.blockPullDown) { on ->
            settings.blockPullDown = on
            on
        }
        rows.note(
            "Puts an invisible strip over the top edge so a swipe there does nothing. If the shade still " +
                "comes down (some phones let it), Toddler Guard closes it straight away."
        )

        rows.header("Volume")
        rows.choiceRow("Highest media volume", Settings.VOLUME_CAPS, settings.volumeCap.toString()) {
            settings.volumeCap = it.toInt()
        }
        rows.note("If the volume is turned up past this, it's turned straight back down. Needs Toddler Guard.")

        rows.header("Screen pinning")
        rows.switchRow("Pin the toddler screen", settings.pinScreen) { on ->
            settings.pinScreen = on
            on
        }
        rows.note("Optional extra. See Lock-down checks for how it works.")
    }

    // ---------- Connections ----------

    private fun buildConnect(rows: Rows) {
        sectionTitle(rows, "Wi-Fi, mobile data and Bluetooth")
        rows.note(
            "Android doesn't let apps switch these on or off themselves (since Android 10), so these buttons " +
                "open Android's own switches. Your child can't change them: Settings and quick settings are " +
                "blocked by Toddler Guard."
        )
        rows.header("Wi-Fi: " + onOff(Device.isWifiOn(this)))
        rows.button("Wi-Fi switch") {
            settings.startParentMode()
            Device.openWifi(this)
        }
        rows.header("Mobile data: " + onOff(Device.isMobileDataInUse(this), "in use", "not in use"))
        rows.button("Internet switches (Wi-Fi and mobile data)") {
            settings.startParentMode()
            Device.openInternet(this)
        }
        rows.header("Bluetooth: " + onOff(Device.isBluetoothOn(this)))
        rows.button("Bluetooth settings") {
            settings.startParentMode()
            Device.openBluetooth(this)
        }
        rows.header("Aeroplane mode: " + onOff(Device.isAirplaneOn(this)))
        rows.button("Aeroplane mode settings") {
            settings.startParentMode()
            Device.openAirplane(this)
        }
        rows.note("Aeroplane mode turns everything off at once, which is a quick way to keep the device offline.")
    }

    private fun onOff(on: Boolean?, yes: String = "on", no: String = "off") = when (on) {
        true -> yes
        false -> no
        null -> "unknown"
    }

    // ---------- Updates and backup ----------

    private fun buildUpdates(rows: Rows) {
        sectionTitle(rows, "Updates and backup")
        rows.header("App updates")
        rows.note("You have version ${Updater.installedVersion(this)}.")
        rows.button("Check for updates or choose a version") { Updater.showDialog(this, settings) }
        rows.header("Backup")
        rows.button("Save settings to a file") { saveBackup() }
        rows.button("Restore settings from a file") { openBackup() }
        rows.note("Includes allowed apps, limits, layout and your PIN or pattern. A wallpaper photo and the usage history aren't included.")
    }

    // ---------- Remote access ----------

    private fun buildRemote(rows: Rows) {
        sectionTitle(rows, "Remote access")
        rows.note(
            "Change every setting, allowed apps and limits, give extra time, start break time, see the usage report " +
                "and add Wi-Fi networks from your own phone's browser. Both devices need to be on the same Wi-Fi " +
                "(or your phone's hotspot). Log in with your parent PIN."
        )
        rows.switchRow("Allow remote access", settings.remoteOn) { on ->
            settings.remoteOn = on
            RemoteService.sync(this)
            col.postDelayed({ build() }, 300)
            on
        }
        if (settings.remoteOn) {
            val addresses = RemoteServer.addresses()
            rows.header("On your phone, open")
            if (addresses.isEmpty()) {
                rows.note("This device isn't on a network right now. Connect it to Wi-Fi or to your phone's hotspot.")
                    .setTextColor(Ui.RED)
            }
            for (a in addresses) {
                rows.col.addView(TextView(this).apply {
                    text = "http://$a:${RemoteServer.PORT}"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextIsSelectable(true)
                    setPadding(0, dp(4), 0, dp(4))
                })
                // QR code of the address: scan it with your phone's camera instead of typing
                val size = minOf(resources.displayMetrics.widthPixels - dp(64), dp(300))
                Qr.bitmap("http://$a:${RemoteServer.PORT}", size)?.let { bmp ->
                    rows.col.addView(android.widget.ImageView(this).apply {
                        setImageBitmap(bmp)
                        contentDescription = "QR code for http://$a:${RemoteServer.PORT}"
                    }, LinearLayout.LayoutParams(size, size).apply { setMargins(0, dp(8), 0, dp(12)) })
                }
            }
            rows.note("Scan the QR code with your phone's camera, or type the address into your browser exactly as shown (http, not https). While it is on, a quiet notification keeps it running even when your child is in another app.")
            if (settings.isDefaultPin()) rows.note("You're still using the default PIN ${Settings.DEFAULT_PIN}. Change it first, as anyone on the same Wi-Fi could try it.").setTextColor(Ui.RED)
            rows.note("While you're connected, a small phone icon shows at the top of the screen, over your child's apps too.")
            rows.header("No Wi-Fi?")
            rows.note(
                "Turn on your phone's hotspot and connect this device to it (Wi-Fi switch in \"Wi-Fi, mobile data " +
                    "and Bluetooth\"). Then open the address above on your phone. A browser can't use Bluetooth " +
                    "or Wi-Fi Direct for this."
            )
            rows.button("Refresh the address") { build() }
        }
    }

    // ---------- Recommended apps ----------

    private var catalog: List<Catalog.Entry>? = null
    private var latest = HashMap<String, Updater.Release?>()
    private var catalogLoading = false
    private var catalogError = ""

    private fun loadCatalog() {
        if (catalogLoading) return
        catalogLoading = true
        Thread {
            val list = Catalog.fetch()
            val found = HashMap<String, Updater.Release?>()
            var error = ""
            for (e in list) {
                try {
                    found[e.pkg] = Updater.fetchReleases(e.repo).firstOrNull()
                } catch (ex: Exception) {
                    error = ex.message ?: "couldn't reach GitHub"
                }
            }
            runOnUiThread {
                catalogLoading = false
                catalog = list
                latest = found
                catalogError = error
                if (!isFinishing) build()
            }
        }.start()
    }

    private fun buildRecommended(rows: Rows) {
        sectionTitle(rows, "Recommended apps")
        rows.note("Other toddler apps made to go with Toddler Launcher. Install one, then switch on " +
            "\"Show on the toddler screen\". Each app has its own parent settings: open it from here to set it up.")
        val list = catalog
        if (list == null) {
            rows.note("Checking GitHub...")
            loadCatalog()
            return
        }
        if (catalogError.isNotEmpty()) rows.note("Couldn't check every app: $catalogError").setTextColor(Ui.RED)
        for (e in list) {
            rows.header(e.name)
            if (e.description.isNotEmpty()) rows.note(e.description)
            val have = Catalog.installedVersion(this, e.pkg)
            val newest = latest[e.pkg]
            val status = when {
                have == null && newest == null -> "Not installed. No download found yet."
                have == null -> "Not installed. Latest version ${newest!!.version}."
                newest == null -> "Installed (version $have)."
                Updater.compare(newest.version, have) > 0 -> "Installed version $have. Version ${newest.version} is available."
                else -> "Installed version $have, the latest."
            }
            rows.note(status)
            if (newest != null && (have == null || Updater.compare(newest.version, have) > 0)) {
                rows.button(if (have == null) "Install ${e.name}" else "Update to ${newest.version}") {
                    Updater.start(this, settings, newest, e.name)
                }
            }
            if (have != null) {
                rows.switchRow("Show on the toddler screen", settings.isAllowed(e.pkg)) { on ->
                    val now = settings.allowed.toMutableList()
                    if (on && !now.contains(e.pkg)) now.add(e.pkg)
                    if (!on) now.remove(e.pkg)
                    settings.allowed = now
                    on
                }
                rows.button("Open ${e.name}") {
                    settings.startParentMode()
                    if (!Apps.launch(this, e.pkg)) Ui.toast(this, "Couldn't open ${e.name}")
                }
            }
        }
        rows.button("Check again") {
            catalog = null
            build()
        }
    }

    // ---------- This device ----------

    private fun buildDevice(rows: Rows) {
        sectionTitle(rows, "This device")
        rows.button("Use my normal home screen for a while") { askOtherHome() }
        rows.note(
            "For system updates or using the device yourself. Protection is paused and Home goes to the home " +
                "screen you pick. To come back early, open Toddler Launcher from its app icon."
        )
        rows.button("Change the home app (back to your normal home screen)") {
            settings.startParentMode()
            Device.openHomeSettings(this)
        }
        rows.note("Pick your normal home app here when you want the device back for good. Pick Toddler Launcher to return.")
        rows.note("After a restart Toddler Launcher opens by itself, as it's the home app, and Toddler Guard comes back on.")
        rows.button("Uninstall Toddler Launcher") {
            settings.startParentMode()
            Device.open(this, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))
        }
    }

    private fun askOtherHome() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val found = packageManager.queryIntentActivities(home, 0)
            .filter { it.activityInfo.packageName != packageName }
        if (found.isEmpty()) {
            Ui.toast(this, "No other home screen app found")
            return
        }
        val names = found.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Which home screen?")
            .setItems(names) { _, which ->
                val ai = found[which].activityInfo
                val cn = ComponentName(ai.packageName, ai.name)
                val times = arrayOf("15 minutes", "1 hour", "Until I open Toddler Launcher again")
                AlertDialog.Builder(this)
                    .setTitle("For how long?")
                    .setItems(times) { _, t ->
                        val now = System.currentTimeMillis()
                        val until = when (t) {
                            0 -> now + 15 * 60_000L
                            1 -> now + 60 * 60_000L
                            else -> Long.MAX_VALUE
                        }
                        settings.otherHome = cn.flattenToString()
                        settings.otherHomeUntil = until
                        settings.pausedUntil = until
                        Device.unpin(this)
                        try {
                            startActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(cn)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            settings.stopOtherHome()
                            Ui.toast(this, "Couldn't open that home screen")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
        if (requestCode == 7 && !Device.isDefaultHome(this)) settings.returnTo = "" // home app not chosen
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
        const val SECTION = "section"
    }
}
