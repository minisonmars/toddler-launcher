package com.toddlerlauncher.parent

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.View
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

/** Toddler Parent's own updates, from the toddler-launcher GitHub releases (the ToddlerParent APK in each). */
object Updater {
    const val REPO = "minisonmars/toddler-launcher"

    class Release(val version: String, val date: String, val apkUrl: String, val sizeBytes: Long, val notes: String)

    fun installed(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    /** Blocking. Newest first, only releases that have a Toddler Parent APK. */
    fun fetchReleases(): List<Release> {
        val conn = URL("https://api.github.com/repos/$REPO/releases?per_page=40").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "ToddlerParent")
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
                    if (a.getString("name").startsWith("ToddlerParent") && a.getString("name").endsWith(".apk")) {
                        out.add(Release(r.getString("tag_name").removePrefix("v"), r.optString("published_at", "").take(10),
                            a.getString("browser_download_url"), a.optLong("size", 0L), r.optString("body", "").trim()))
                        break
                    }
                }
            }
            return out.sortedWith { x, y -> compare(y.version, x.version) }
        } finally {
            conn.disconnect()
        }
    }

    /** Blocking. The newest version if it's newer than this one. */
    fun newer(ctx: Context): Release? = try {
        fetchReleases().firstOrNull()?.takeIf { compare(it.version, installed(ctx)) > 0 }
    } catch (e: Exception) {
        null
    }

    fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "ToddlerParent")
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
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Gives the APK to Android's installer. On Android 12 and later, once this app installed
     * the previous version itself, the update goes straight in with no confirm screen;
     * otherwise Android shows its own "Update?" screen (see InstallReceiver).
     */
    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        // Tells Android up front that this is an update to this app. Without it Android can't
        // tell the session is an update and always shows its confirm screen.
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }

    // ---------- The versions screen ----------

    fun showDialog(activity: Activity) {
        val status = TextView(activity).apply {
            text = "Checking GitHub for versions..."
            textSize = 16f
            val pad = Ui.dp(activity, 24)
            setPadding(pad, pad, pad, pad)
        }
        val checking = AlertDialog.Builder(activity).setTitle("App updates").setView(status)
            .setNegativeButton("Close", null).show()
        Thread {
            try {
                val list = fetchReleases()
                activity.runOnUiThread {
                    if (activity.isFinishing || !checking.isShowing) return@runOnUiThread
                    checking.dismiss()
                    showList(activity, list)
                }
            } catch (e: Exception) {
                activity.runOnUiThread { status.text = "Couldn't check for updates: ${e.message}" }
            }
        }.start()
    }

    /** Newer versions with what changed, then the installed version, then older versions behind a button. */
    private fun showList(activity: Activity, list: List<Release>) {
        val current = installed(activity)
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
        fun button(label: String, onClick: () -> Unit) = Button(activity).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        fun info(r: Release) = String.format(Locale.UK, "%s  ·  %.1f MB", r.date, r.sizeBytes / 1_048_576.0)
        fun notes(r: Release) = r.notes.ifBlank { "No notes for this version." }
        // Every version gets its own button under its notes, so any version can be picked
        fun versionButton(label: String, onClick: () -> Unit) = Button(activity).apply {
            text = label
            isAllCaps = false
            setOnClickListener { dialog.dismiss(); onClick() }
        }

        val newer = list.filter { compare(it.version, current) > 0 }
        if (newer.isEmpty()) {
            col.addView(text("You have the latest version.", 16f))
        } else {
            col.addView(text(if (newer.size == 1) "New version" else "${newer.size} new versions", 18f, bold = true))
            for (r in newer) {
                col.addView(text("Version ${r.version}" + if (r === newer.first()) "  (latest)" else "", 17f, bold = true)
                    .apply { setPadding(0, dp(12), 0, 0) })
                col.addView(text(info(r), 13f, colour = Ui.GREY))
                col.addView(text(notes(r), 15f))
                col.addView(versionButton("Install version ${r.version}") { start(activity, r) })
            }
        }

        val installedRelease = list.firstOrNull { it.version == current }
        col.addView(text("Installed: version $current", 18f, bold = true, colour = Ui.GREEN)
            .apply { if (newer.isNotEmpty()) setPadding(0, dp(22), 0, dp(4)) })
        if (installedRelease != null) col.addView(text(notes(installedRelease), 14f, colour = Ui.GREY))
        if (installedRelease != null) {
            col.addView(button("Reinstall version $current") { dialog.dismiss(); start(activity, installedRelease) })
        }

        val older = list.filter { compare(it.version, current) < 0 }
        if (older.isNotEmpty()) {
            val olderBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val showOlder = button("Show older versions (${older.size})") {}
            showOlder.setOnClickListener {
                showOlder.visibility = View.GONE
                olderBox.visibility = View.VISIBLE
            }
            col.addView(showOlder)
            col.addView(olderBox)
            olderBox.addView(text("Older versions", 18f, bold = true).apply { setPadding(0, dp(22), 0, dp(4)) })
            for (r in older) {
                olderBox.addView(text("Version ${r.version}", 17f, bold = true).apply { setPadding(0, dp(12), 0, 0) })
                olderBox.addView(text(info(r), 13f, colour = Ui.GREY))
                olderBox.addView(text(notes(r), 14f, colour = Ui.GREY))
                olderBox.addView(versionButton("Go back to version ${r.version}") { explainOlder(activity, r) })
            }
        }
        dialog = AlertDialog.Builder(activity).setTitle("App versions")
            .setView(ScrollView(activity).apply { addView(col) })
            .setNegativeButton("Close", null).create()
        dialog.show()
    }

    private fun explainOlder(activity: Activity, r: Release) {
        AlertDialog.Builder(activity)
            .setTitle("Go back to ${r.version}?")
            .setMessage(
                "Android won't install an older version over a newer one. To go back:\n\n" +
                    "1. Tap Download below. Your browser saves the older APK.\n" +
                    "2. Uninstall Toddler Parent (this forgets your paired devices).\n" +
                    "3. Open the downloaded APK from your Downloads to install it.\n" +
                    "4. Pair your child's devices again with the parent PIN."
            )
            .setPositiveButton("Download") { _, _ ->
                try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl))) } catch (e: Exception) {}
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun start(activity: Activity, r: Release) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity).setTitle("Allow updates")
                .setMessage("Android needs you to allow Toddler Parent to install updates. Tap Open settings, switch on " +
                    "\"Allow from this source\", come back and try again.")
                .setPositiveButton("Open settings") { _, _ ->
                    try {
                        activity.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")))
                    } catch (e: Exception) {}
                }
                .setNegativeButton("Cancel", null).show()
            return
        }
        val status = TextView(activity).apply {
            text = "Downloading ${r.version}..."
            textSize = 16f
            val pad = Ui.dp(activity, 24)
            setPadding(pad, pad, pad, pad)
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Updating").setView(status).setCancelable(false).show()
        val file = File(activity.cacheDir, "update.apk")
        Thread {
            try {
                download(r.apkUrl, file) { p -> activity.runOnUiThread { status.text = "Downloading ${r.version}... $p%" } }
                activity.runOnUiThread { status.text = "Installing... If Android asks, tap Update. The app closes when the update is in; open it again." }
                install(activity, file)
                activity.runOnUiThread { status.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, 4000) }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    Ui.toast(activity, "Download failed: ${e.message}")
                }
            }
        }.start()
    }
}
