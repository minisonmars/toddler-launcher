package com.toddlerlauncher.parent

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONArray
import org.json.JSONObject

/** Everything a parent can change on one child device, the same as its parent area. */
class ChildActivity : Activity() {

    private lateinit var rows: Rows
    private lateinit var child: Child
    private var st: JSONObject? = null
    private var problem = ""
    private var busy = false
    private val handler = Handler(Looper.getMainLooper())
    private val keepAlive = object : Runnable {
        override fun run() {
            load(quiet = true)
            handler.postDelayed(this, 45_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = Store.get(this, intent.getStringExtra("id") ?: "")
        if (c == null) {
            finish()
            return
        }
        child = c
        rows = Rows(this, Ui.page(this))
        build()
    }

    override fun onResume() {
        super.onResume()
        Finder.start(this)
        handler.post(keepAlive)
    }

    override fun onPause() {
        handler.removeCallbacks(keepAlive)
        // Tell the toddler device we've gone, so its "parent connected" icon goes
        val c = child
        Thread { try { Net.call(applicationContext, c, "/api/bye") } catch (e: Exception) {} }.start()
        super.onPause()
    }

    /** Runs a request in the background, then reloads the screen. */
    private fun act(path: String, body: JSONObject = JSONObject(), done: String = "Done", reload: Boolean = true) {
        Thread {
            val msg = try {
                val r = Net.call(this, child, path, body)
                r.optString("message", done)
            } catch (e: LinkError) {
                e.message ?: "Something went wrong"
            } catch (e: Exception) {
                "Something went wrong: ${e.message}"
            }
            runOnUiThread {
                if (!isFinishing) {
                    Ui.toast(this, msg)
                    if (reload) handler.postDelayed({ load() }, 400)
                }
            }
        }.start()
    }

    private fun load(quiet: Boolean = false) {
        if (busy) return
        busy = true
        if (!quiet && st == null) build()
        Thread {
            var s: JSONObject? = null
            var p = ""
            try {
                s = Net.call(this, child, "/api/state")
            } catch (e: LinkError) {
                p = e.message ?: ""
            } catch (e: Exception) {
                p = "Something went wrong: ${e.message}"
            }
            runOnUiThread {
                busy = false
                if (isFinishing) return@runOnUiThread
                problem = p
                if (s != null) {
                    // Only redraw when something changed, so the screen doesn't jump while you use it
                    val changed = st?.toString() != s.toString()
                    st = s
                    val n = s.optString("name")
                    if (n.isNotEmpty() && n != child.name) {
                        child.name = n
                        Store.save(this, child)
                    }
                    if (changed || !quiet) build()
                } else if (!quiet || st == null) build()
            }
        }.start()
    }

    /** null = the menu; otherwise the key of the section on screen (same as the launcher's parent area). */
    private var section: String? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (section != null) {
            section = null
            build()
            (rows.col.parent as? android.widget.ScrollView)?.let { sv -> sv.post { sv.scrollTo(0, 0) } }
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun open(key: String) {
        section = key
        build()
        (rows.col.parent as? android.widget.ScrollView)?.let { sv -> sv.post { sv.scrollTo(0, 0) } }
    }

    private fun build() {
        // Keep the scroll position when the screen is refreshed
        val scroll = rows.col.parent as? android.widget.ScrollView
        val y = scroll?.scrollY ?: 0
        scroll?.post { scroll.scrollTo(0, y) }
        rows.clear()
        val s = st
        if (s == null) {
            rows.title(child.name)
            if (problem.isEmpty()) rows.note("Connecting...")
            else {
                rows.note(problem, Ui.RED, 16f)
                rows.button("Try again") { load() }
            }
            footer()
            return
        }
        val key = section
        if (key == null) buildMenu(s) else {
            val def = com.toddlerlauncher.Sections.get(key)
            rows.title(def?.title ?: key)
            rows.button("Back") { section = null; build() }
            if (problem.isNotEmpty()) rows.note(problem, Ui.RED)
            when (key) {
                "checks" -> buildChecks(s)
                "apps" -> buildApps(s)
                "recommended" -> rows.note("Install, update and open recommended apps from the parent area on the toddler device itself: Android only lets an app install other apps with someone tapping on that device.")
                "time" -> buildTime(s)
                "report" -> buildReport(s)
                "look" -> buildLook(s)
                "code" -> buildCode(s)
                "protect" -> buildProtect(s)
                "connect" -> buildConnect(s)
                "remote" -> buildRemote(s)
                "updates" -> buildUpdates(s)
                "device" -> buildDevice(s)
            }
        }
    }

    private fun buildMenu(s: JSONObject) {
        rows.title(child.name, "${s.optString("device")} · version ${s.optString("version")}")
        if (problem.isNotEmpty()) rows.note(problem, Ui.RED)
        connection(s)

        // Now: the quick things, before the sections
        rows.header("Now")
        nowStatus(s)
        rows.buttons(
            "+15 minutes" to { act("/api/bonus", JSONObject().put("min", 15), "15 minutes added") },
            "+30 minutes" to { act("/api/bonus", JSONObject().put("min", 30), "30 minutes added") },
        )
        if (s.optBoolean("lockedNow")) rows.button("End break time") { act("/api/lock", JSONObject().put("on", false), "Break time is over") }
        else rows.button("Break time now") { act("/api/lock", JSONObject().put("on", true), "Break time") }
        rows.button("Refresh") { load() }

        rows.header("Parent area")
        val secs = s.optJSONArray("sections")
        if (secs != null) {
            for (i in 0 until secs.length()) {
                val o = secs.getJSONObject(i)
                val def = com.toddlerlauncher.Sections.get(o.optString("key")) ?: continue
                rows.menuItem(def.title, o.optString("subtitle"), o.optBoolean("warn"), def.icon, def.colour) { open(def.key) }
            }
        } else {
            rows.note("Update Toddler Launcher on the toddler device to see its parent area here.", Ui.ORANGE)
        }
        footer()
    }

    private fun nowStatus(s: JSONObject) {
        val allowance = s.optString("allowance")
        rows.big("Today: ${s.optString("usedToday")}" + if (allowance != "No limit") " of $allowance" else "")
        if (s.optInt("bonus") > 0) rows.note("Including ${s.optInt("bonus")} extra minutes today.")
        if (s.optString("block").isNotEmpty()) rows.note("Toddler screen: ${s.optString("block")}", Ui.ORANGE)
        if (s.optBoolean("paused")) rows.note(s.optString("pausedText"), Ui.ORANGE)
        if (!s.optBoolean("guardEnabled", true)) rows.note("Toddler Guard is off in Android, so limits and blocking are not working.", Ui.RED)
    }

    /** The settings of one section, under the same headings as on the device. [extra] adds things after a heading's settings. */
    private fun schemaSection(s: JSONObject, key: String, extra: (String) -> Unit = {}) {
        val settings = s.optJSONArray("settings") ?: JSONArray()
        val items = (0 until settings.length()).map { settings.getJSONObject(it) }.filter { it.optString("section") == key }
        val hoursOn = items.firstOrNull { it.optString("key") == "hoursOn" }?.optString("value") == "true"
        var header: String? = null
        for (item in items) {
            val h = item.optString("header")
            if (h != header) {
                if (header != null) extra(header)
                header = h
                rows.header(h)
                extra("before:$h")
            }
            if (h == "Allowed hours" && item.optString("key") != "hoursOn" && !hoursOn) continue
            settingRow(item)
        }
        if (header != null) extra(header)
    }

    private fun buildChecks(s: JSONObject) {
        val checks = s.optJSONArray("checks") ?: JSONArray()
        for (i in 0 until checks.length()) {
            val c = checks.getJSONObject(i)
            rows.check(c.optString("title"), c.optBoolean("done"))
        }
        rows.note("To fix a cross, open Lock-down checks in the parent area on the toddler device: these are Android settings that only someone at that device can change.")
    }

    private fun buildApps(s: JSONObject) {
        val apps = s.optJSONArray("apps") ?: JSONArray()
        val limits = pairs(s.optJSONArray("appLimitOptions"))
        rows.header("Allowed apps")
        rows.note("Switch on the apps your child can use.")
        for (i in 0 until apps.length()) {
            val a = apps.getJSONObject(i)
            val pkg = a.optString("pkg")
            rows.switchRow(a.optString("label"), a.optBoolean("allowed")) { on ->
                act("/api/app", JSONObject().put("pkg", pkg).put("allowed", on), if (on) "Allowed" else "Removed")
            }
        }
        rows.header("Daily time limit for each app")
        var any = false
        for (i in 0 until apps.length()) {
            val a = apps.getJSONObject(i)
            if (!a.optBoolean("allowed")) continue
            any = true
            val pkg = a.optString("pkg")
            rows.choiceRow("${a.optString("label")} (today: ${a.optString("today")})", limits, a.optInt("limit").toString()) {
                act("/api/app", JSONObject().put("pkg", pkg).put("limit", it.toInt()), "Saved", reload = false)
            }
        }
        if (!any) rows.note("No apps allowed yet.")
    }

    private fun buildTime(s: JSONObject) {
        if (!s.optBoolean("guardEnabled", true)) rows.note("Toddler Guard is off in Android, so screen time can't be counted or limited. Switch it on from Lock-down checks on the device.", Ui.RED)
        schemaSection(s, "time") { h ->
            when (h) {
                "before:Daily limit" -> rows.note("Counts time spent inside the apps, not on the toddler home screen.")
                "Daily limit" -> {
                    nowStatus(s)
                    rows.buttons(
                        "Give 15 more minutes today" to { act("/api/bonus", JSONObject().put("min", 15), "15 minutes added") },
                        "+30 minutes" to { act("/api/bonus", JSONObject().put("min", 30), "30 minutes added") },
                    )
                    rows.note("Extra time is added to the daily limit and to each app's limit, for today only.")
                    rows.header("Break time")
                    if (s.optBoolean("lockedNow")) rows.button("End break time") { act("/api/lock", JSONObject().put("on", false), "Break time is over") }
                    else rows.button("Break time now") { act("/api/lock", JSONObject().put("on", true), "Break time") }
                    rows.note("Locks the toddler screen and every app until you end it.")
                }
                "before:Allowed hours" -> rows.note("Outside these times the toddler screen shows \"Sleep time\" and no apps open.")
            }
        }
    }

    private fun buildReport(s: JSONObject) {
        val days = s.optJSONArray("reportDays") ?: JSONArray()
        val dayNames = (0 until days.length()).map { days.optString(it) }
        val report = s.optJSONArray("report") ?: JSONArray()
        for (i in 0 until report.length()) {
            val r = report.getJSONObject(i)
            val d = r.optJSONArray("days") ?: JSONArray()
            val line = dayNames.indices.joinToString("  ·  ") { "${dayNames[it]} ${d.optString(it)}" } + "  ·  7-day avg ${r.optString("avg")}"
            rows.note(r.optString("label"), if (r.optBoolean("total")) Ui.PURPLE else android.graphics.Color.GRAY, 16f)
                .apply { typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, Ui.dp(this@ChildActivity, 8), 0, 0) }
            rows.note(line)
        }
        rows.note("Time in apps in toddler mode. The average is the last 7 days divided by 7.")
    }

    private fun buildLook(s: JSONObject) {
        schemaSection(s, "look") { h ->
            if (h == "before:Child's name") {
                rows.button(if (s.optString("childName").isBlank()) "Set your child's name" else "Name: ${s.optString("childName")}") {
                    askText("Child's name", "Shown at the top of the toddler screen in colourful letters. Leave it empty to hide it.", s.optString("childName")) {
                        act("/api/name", JSONObject().put("name", it), "Name saved")
                    }
                }
            }
            if (h == "Wallpaper") rows.note("To use a photo, choose it on the toddler device (Look and layout > Choose a photo).")
        }
    }

    private fun buildCode(s: JSONObject) {
        rows.header("Code")
        if (s.optBoolean("defaultPin")) rows.note("Still using the default PIN 0000.", Ui.RED)
        rows.note("Opens the parent area with: " + (if (s.optString("lockType") == "pattern") "pattern" else "PIN") +
            ". The pattern and recovery word are set on the device.")
        rows.button("Change PIN") { askPin() }
        schemaSection(s, "code")
    }

    private fun buildProtect(s: JSONObject) {
        schemaSection(s, "protect") { h ->
            if (h == "Toddler Guard") {
                if (s.has("guardStatus")) rows.note("Status: " + s.optString("guardStatus"))
                if (s.optBoolean("paused")) {
                    rows.note(s.optString("pausedText"), Ui.ORANGE)
                    rows.button("Switch protection back on") { act("/api/pause", JSONObject().put("min", 0), "Protection on") }
                } else rows.buttons(
                    "Pause 15 min" to { act("/api/pause", JSONObject().put("min", 15), "Paused") },
                    "Pause 1 hour" to { act("/api/pause", JSONObject().put("min", 60), "Paused") },
                )
            }
        }
    }

    private fun buildConnect(s: JSONObject) {
        fun onOff(b: Boolean, on: String = "on", off: String = "off") = if (b) on else off
        rows.header("Wi-Fi: " + onOff(s.optBoolean("wifiOn")))
        rows.header("Mobile data: " + onOff(s.optBoolean("mobileData"), "in use", "not in use"))
        rows.header("Bluetooth: " + onOff(s.optBoolean("bluetoothOn")))
        rows.header("Aeroplane mode: " + onOff(s.optBoolean("airplaneOn")))
        rows.note("Android doesn't let apps switch these, so they can only be changed on the device.")
        rows.header("Wi-Fi networks")
        if (!s.optBoolean("wifiSupported")) {
            rows.note("Adding networks needs Android 10 or newer on the toddler device.")
        } else {
            rows.note("Add a network and the toddler device joins it by itself whenever it's in range.")
            rows.button("Add a Wi-Fi network") { askWifi() }
            val nets = s.optJSONArray("wifiNetworks") ?: JSONArray()
            for (i in 0 until nets.length()) {
                val n = nets.optString(i)
                rows.item(n, "Tap to remove") {
                    AlertDialog.Builder(this).setTitle("Remove \"$n\"?")
                        .setPositiveButton("Remove") { _, _ -> act("/api/wifi/remove", JSONObject().put("ssid", n)) }
                        .setNegativeButton("Cancel", null).show()
                }
            }
        }
    }

    private fun buildRemote(s: JSONObject) {
        connection(s)
        rows.header("Paired phones")
        val names = s.optJSONArray("pairedNames") ?: JSONArray()
        for (i in 0 until names.length()) rows.note(names.optString(i), size = 16f)
        rows.note("Remove other phones from Parent area > Remote access on the device.")
        rows.header("This phone")
        rows.button("Unpair this phone") { unpair() }
    }

    private fun buildUpdates(s: JSONObject) {
        rows.header("App updates")
        rows.note("The toddler device has version ${s.optString("version")}.", size = 16f)
        val v = s.optString("latestVersion")
        if (v.isNotEmpty()) rows.note("Version $v is ready. Install it from Updates and backup on the device (Android asks someone there to confirm).", Ui.RED)
        else rows.note("It's up to date (as of its last check).")
        rows.button("Updates for this app (Toddler Parent)") { Updater.showDialog(this) }
        rows.header("Backup")
        rows.note("Save and restore settings files on the device itself.")
    }

    private fun buildDevice(s: JSONObject) {
        rows.header("About")
        rows.note("${s.optString("device")}, Android ${s.optString("android")}", size = 16f)
        if (s.has("battery")) rows.note("Battery ${s.optInt("battery")}%" + if (s.optBoolean("charging")) " (charging)" else "")
        rows.note(if (s.optBoolean("defaultHome", true)) "Toddler Launcher is the home app." else "Toddler Launcher is NOT the home app.",
            if (s.optBoolean("defaultHome", true)) Ui.GREY else Ui.RED)
        rows.note("Using your normal home screen for a while, changing the home app and uninstalling are done on the device.")
    }

    /** How the two are connected, and other details that help when something isn't working. */
    private fun connection(s: JSONObject) {
        rows.header("Connection")
        val c = Net.lastConn[child.id]
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.UK)
        if (c != null) {
            val how = when (c.via) {
                "direct" -> "Directly, over its private Wi-Fi \"${Direct.ssid}\""
                "found" -> "Same Wi-Fi (found automatically)"
                else -> "Same Wi-Fi (last address that worked)"
            }
            rows.note(how, Ui.GREEN, 16f)
            rows.note("Address ${c.host}:${c.port}  ·  answered in ${c.ms} ms  ·  updated ${time.format(java.util.Date(c.at))}")
        }
        rows.note("Locked with this phone's own key (AES-256). Check code ${com.toddlerlauncher.link.Link.checkCode(child.key)}")
        val phone = Net.myAddresses()
        if (phone.isNotEmpty()) rows.note("This phone: " + phone.joinToString(", "))
        val bits = mutableListOf<String>()
        bits.add("Android ${s.optString("android", "?")}")
        if (s.has("battery")) bits.add("battery ${s.optInt("battery")}%" + if (s.optBoolean("charging")) " (charging)" else "")
        val addrs = s.optJSONArray("addresses")
        if (addrs != null && addrs.length() > 0) bits.add("addresses " + (0 until addrs.length()).joinToString(", ") { addrs.optString(it) })
        if (s.optBoolean("direct")) bits.add("private Wi-Fi on (\"${s.optString("directSsid")}\")")
        bits.add("${s.optInt("pairedPhones")} paired phone" + if (s.optInt("pairedPhones") == 1) "" else "s")
        if (s.has("defaultHome")) bits.add(if (s.optBoolean("defaultHome")) "is the home app" else "NOT the home app")
        bits.add(if (s.optBoolean("guardEnabled")) "Toddler Guard on" else "Toddler Guard OFF")
        rows.note("Toddler device: " + bits.joinToString("  ·  "))
        if (s.has("guardStatus")) rows.note("Toddler Guard: " + s.optString("guardStatus"),
            if (s.optString("guardStatus").startsWith("Working")) Ui.GREY else Ui.ORANGE)
        val skew = (s.optLong("clock") - System.currentTimeMillis()) / 1000
        if (s.has("clock") && kotlin.math.abs(skew) > 60) {
            rows.note("The two clocks are ${kotlin.math.abs(skew) / 60} minutes apart. Over 10 minutes and they can't talk, so check the time on both.", Ui.ORANGE)
        }
    }

    private fun footer() {
        rows.header("This phone")
        rows.button("Unpair this phone") { unpair() }
    }

    private fun unpair() {
        AlertDialog.Builder(this).setTitle("Unpair from ${child.name}?")
            .setMessage("You'll need the parent PIN to pair again.")
            .setPositiveButton("Unpair") { _, _ ->
                Thread {
                    try { Net.call(this, child, "/api/unpair") } catch (e: Exception) {}
                    Store.remove(this, child.id)
                    runOnUiThread { finish() }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pairs(arr: JSONArray?): List<Pair<String, String>> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { val p = arr.getJSONArray(it); p.optString(0) to p.optString(1) }
    }

    private fun settingRow(item: JSONObject) {
        val key = item.optString("key")
        val label = item.optString("label")
        val value = item.optString("value")
        val type = item.optString("type")
        fun set(v: Any) = act("/api/set", JSONObject().put("key", key).put("value", v), "Saved", reload = key == "hoursOn" || type == "time")
        when (item.optString("type")) {
            "bool" -> rows.switchRow(label, value == "true") { set(it) }
            "time" -> {
                val m = value.toIntOrNull() ?: 0
                rows.button("$label: %02d:%02d".format(m / 60, m % 60)) {
                    TimePickerDialog(this, { _, h, mm -> set((h * 60 + mm).toString()) }, m / 60, m % 60, true).show()
                }
            }
            else -> {
                val opts = pairs(item.optJSONArray("options"))
                if (opts.isNotEmpty()) rows.choiceRow(label, opts, value) { set(it) }
            }
        }
    }

    private fun askText(title: String, message: String, current: String, onSave: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(current)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setView(padded(field))
            .setPositiveButton("Save") { _, _ -> onSave(field.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askWifi() {
        val ssid = EditText(this).apply { hint = "Network name" }
        val pass = EditText(this).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ssid)
            addView(pass)
        }
        AlertDialog.Builder(this).setTitle("Add a Wi-Fi network")
            .setView(padded(box))
            .setPositiveButton("Add") { _, _ ->
                act("/api/wifi/add", JSONObject().put("ssid", ssid.text.toString().trim()).put("password", pass.text.toString()))
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askPin() {
        fun pinField(h: String) = EditText(this).apply {
            hint = h
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        val cur = pinField("Current PIN")
        val new = pinField("New PIN (4 to 8 digits)")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(cur)
            addView(new)
        }
        AlertDialog.Builder(this).setTitle("Change the parent PIN")
            .setMessage("This phone stays paired.")
            .setView(padded(box))
            .setPositiveButton("Change") { _, _ ->
                act("/api/pin", JSONObject().put("current", cur.text.toString()).put("new", new.text.toString()), "PIN changed")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun padded(v: android.view.View) = android.widget.FrameLayout(this).apply {
        setPadding(Ui.dp(this@ChildActivity, 24), 0, Ui.dp(this@ChildActivity, 24), 0)
        addView(v)
    }
}
