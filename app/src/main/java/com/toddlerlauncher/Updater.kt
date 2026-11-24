package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.graphics.Typeface
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Lists the app's GitHub releases, downloads one and hands it to Android to install. */
object Updater {

    const val OWN_REPO = "minisonmars/toddler-launcher"

    class Release(val version: String, val date: String, val apkUrl: String, val sizeBytes: Long, val notes: String = "")

    fun installedVersion(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }

    /** Blocking, so call it off the main thread. Newest first, only releases that have an APK. */
    fun fetchReleases(repo: String = OWN_REPO): List<Release> {
        val conn = URL("https://api.github.com/repos/$repo/releases?per_page=40").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ToddlerLauncher")
            if (conn.responseCode == 404) return emptyList()
            if (conn.responseCode == 403) throw IOException("GitHub is busy, try again in a few minutes")
            if (conn.responseCode != 200) throw IOException("GitHub replied ${conn.responseCode}")
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val out = mutableListOf<Release>()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val assets = r.getJSONArray("assets")
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.getString("name").endsWith(".apk")) {
                        out.add(
                            Release(
                                r.getString("tag_name").removePrefix("v"),
                                r.optString("published_at", "").take(10),
                                a.getString("browser_download_url"),
                                a.optLong("size", 0L),
                                r.optString("body", "").trim()
                            )
                        )
                        break
                    }
                }
            }
            return out.sortedWith { x, y -> compare(y.version, x.version) }
        } finally {
            conn.disconnect()
        }
    }

    /** Compares version numbers like 0.1.10 and 0.1.9 part by part. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    /** Blocking, so call it off the main thread. [onProgress] gets 0 to 100. */
    fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "ToddlerLauncher")
            if (conn.responseCode != 200) throw IOException("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != last) {
                                last = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Gives the APK to Android's installer. Android shows its own "Update?" screen. */
    fun install(ctx: Context, apk: File, label: String = "") {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallReceiver::class.java).putExtra(InstallReceiver.LABEL, label)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    // ---------- The update screens, used from the parent area and (for now) the home screen ----------

    /** Shows the list of versions: install the latest, or pick another one. */
    fun showDialog(activity: Activity, settings: Settings) {
        settings.startParentMode() // Android's installer must not be blocked by Toddler Guard
        val status = TextView(activity).apply {
            text = "Checking GitHub for versions..."
            textSize = 16f
            val pad = Ui.dp(activity, 24)
            setPadding(pad, pad, pad, pad)
        }
        val checking = AlertDialog.Builder(activity)
            .setTitle("App updates")
            .setView(status)
            .setNegativeButton("Close", null)
            .show()
        Thread {
            try {
                val list = fetchReleases()
                activity.runOnUiThread {
                    if (activity.isFinishing || !checking.isShowing) return@runOnUiThread
                    checking.dismiss()
                    showList(activity, settings, list)
                }
            } catch (e: Exception) {
                activity.runOnUiThread { status.text = "Couldn't check for updates: ${e.message}" }
            }
        }.start()
    }

    /** Installed version, then each newer version with what changed, then older versions in a drop-down. */
    private fun showList(activity: Activity, settings: Settings, list: List<Release>) {
        val current = installedVersion(activity)
        if (list.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("App updates")
                .setMessage("No versions found on GitHub yet. You have $current.")
                .setPositiveButton("OK", null).show()
            return
        }
        fun dp(v: Int) = Ui.dp(activity, v)
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        lateinit var dialog: AlertDialog
        fun text(s: String, size: Float, bold: Boolean = false, colour: Int? = null) = TextView(activity).apply {
            text = s
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (colour != null) setTextColor(colour)
            setPadding(0, dp(4), 0, dp(4))
        }
        fun info(r: Release) = String.format(Locale.UK, "%s  ·  %.1f MB", r.date, r.sizeBytes / 1_048_576.0)
        fun notes(r: Release) = r.notes.ifBlank { "No notes for this version." }

        val installed = list.firstOrNull { it.version == current }
        col.addView(text("Installed: version $current", 18f, bold = true, colour = Ui.GREEN))
        if (installed != null) col.addView(text(notes(installed), 14f, colour = Ui.GREY))

        val newer = list.filter { compare(it.version, current) > 0 }
        if (newer.isEmpty()) {
            col.addView(text("You have the latest version.", 16f))
        } else {
            col.addView(text(if (newer.size == 1) "New version" else "${newer.size} new versions", 18f, bold = true)
                .apply { setPadding(0, dp(18), 0, dp(4)) })
            for (r in newer) {
                col.addView(text("Version ${r.version}" + if (r === newer.first()) "  (latest)" else "", 17f, bold = true)
                    .apply { setPadding(0, dp(12), 0, 0) })
                col.addView(text(info(r), 13f, colour = Ui.GREY))
                col.addView(text(notes(r), 15f))
            }
            col.addView(Button(activity).apply {
                text = "Install version ${newer.first().version}"
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    start(activity, settings, newer.first())
                }
            })
        }

        val older = list.filter { compare(it.version, current) < 0 }
        if (older.isNotEmpty()) {
            // Older versions stay out of the way until asked for
            val olderBox = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                visibility = android.view.View.GONE
            }
            val showOlder = Button(activity).apply {
                text = "Show older versions (${older.size})"
                isAllCaps = false
            }
            showOlder.setOnClickListener {
                showOlder.visibility = android.view.View.GONE
                olderBox.visibility = android.view.View.VISIBLE
            }
            col.addView(showOlder)
            col.addView(olderBox)
            olderBox.addView(text("Older versions", 18f, bold = true).apply { setPadding(0, dp(22), 0, dp(4)) })
            val spinner = Spinner(activity)
            val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, older.map { "${it.version}  (${it.date})" })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            olderBox.addView(spinner)
            val olderNotes = text(notes(older.first()), 14f, colour = Ui.GREY)
            olderBox.addView(olderNotes)
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    olderNotes.text = notes(older[pos])
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
            olderBox.addView(Button(activity).apply {
                text = "Go back to this version"
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    explainOlder(activity, settings, older[spinner.selectedItemPosition.coerceAtLeast(0)])
                }
            })
        }
        if (installed != null) {
            col.addView(Button(activity).apply {
                text = "Reinstall version $current"
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    choose(activity, settings, installed, current)
                }
            })
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle("App versions")
            .setView(ScrollView(activity).apply { addView(col) })
            .setNegativeButton("Close", null)
            .create()
        dialog.show()
    }

    private fun choose(activity: Activity, settings: Settings, r: Release, current: String) {
        val c = compare(r.version, current)
        if (c < 0) {
            explainOlder(activity, settings, r)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(if (c == 0) "Reinstall ${r.version}?" else "Install ${r.version}?")
            .setMessage("Your settings and allowed apps are kept.")
            .setPositiveButton("Install") { _, _ -> start(activity, settings, r) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Android refuses to put an older version over a newer one, so that needs an uninstall first. */
    private fun explainOlder(activity: Activity, settings: Settings, r: Release) {
        AlertDialog.Builder(activity)
            .setTitle("Go back to ${r.version}?")
            .setMessage(
                "Android won't install an older version over a newer one. To go back:\n\n" +
                    "1. In the parent area, use Backup > Save settings to a file (optional).\n" +
                    "2. Tap Download below. Your browser saves the older APK.\n" +
                    "3. Uninstall Toddler Launcher (button in the parent area, or Android settings).\n" +
                    "4. Open the downloaded APK from your Downloads to install it.\n" +
                    "5. Restore your settings from the file, and run setup again."
            )
            .setPositiveButton("Download") { _, _ ->
                settings.startParentMode()
                Device.open(activity, Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl)))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Downloads [r] and installs it. [label] is set when it's another app (one of the recommended apps). */
    fun start(activity: Activity, settings: Settings, r: Release, label: String = "") {
        if (!Device.canInstallUpdates(activity)) {
            AlertDialog.Builder(activity)
                .setTitle("Allow updates")
                .setMessage(
                    "Android needs you to allow this app to install updates. Tap Open settings, switch on " +
                        "\"Allow from this source\", come back and try again."
                )
                .setPositiveButton("Open settings") { _, _ ->
                    settings.startParentMode()
                    Device.openInstallPermission(activity)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        Device.unpin(activity) // screen pinning would block Android's install screen
        settings.startParentMode()
        settings.installingUntil = System.currentTimeMillis() + 5 * 60_000L
        val status = TextView(activity).apply {
            text = "Downloading ${r.version}..."
            textSize = 16f
            val pad = Ui.dp(activity, 24)
            setPadding(pad, pad, pad, pad)
        }
        val dialog = AlertDialog.Builder(activity).setTitle(if (label.isEmpty()) "Updating" else "Installing $label")
            .setView(status).setCancelable(false).show()
        val file = File(activity.cacheDir, if (label.isEmpty()) "update.apk" else "app.apk")
        Thread {
            try {
                download(r.apkUrl, file) { percent ->
                    activity.runOnUiThread { status.text = "Downloading ${r.version}... $percent%" }
                }
                activity.runOnUiThread { status.text = "Installing... confirm on the screen that appears" }
                install(activity, file, label)
                activity.runOnUiThread { status.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, 4000) }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    settings.installingUntil = 0L
                    Ui.toast(activity, "Download failed: ${e.message}")
                }
            }
        }.start()
    }
}
