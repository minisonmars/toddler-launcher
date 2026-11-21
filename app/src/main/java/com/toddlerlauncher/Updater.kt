package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.TextView
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Lists the app's GitHub releases, downloads one and hands it to Android to install. */
object Updater {

    private const val RELEASES = "https://api.github.com/repos/minisonmars/toddler-launcher/releases?per_page=40"

    class Release(val version: String, val date: String, val apkUrl: String, val sizeBytes: Long)

    fun installedVersion(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }

    /** Blocking, so call it off the main thread. Newest first, only releases that have an APK. */
    fun fetchReleases(): List<Release> {
        val conn = URL(RELEASES).openConnection() as HttpURLConnection
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
                                a.optLong("size", 0L)
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
    fun install(ctx: Context, apk: File) {
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
            val intent = Intent(ctx, InstallReceiver::class.java)
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

    private fun showList(activity: Activity, settings: Settings, list: List<Release>) {
        val current = installedVersion(activity)
        if (list.isEmpty()) {
            AlertDialog.Builder(activity).setTitle("App updates")
                .setMessage("No versions found on GitHub yet. You have $current.")
                .setPositiveButton("OK", null).show()
            return
        }
        val latest = list.first()
        val labels = list.map { r ->
            val tag = when {
                r.version == current -> "  (installed)"
                r === latest -> "  (latest)"
                else -> ""
            }
            String.format(Locale.UK, "%s   %s   %.1f MB%s", r.version, r.date, r.sizeBytes / 1_048_576.0, tag)
        }.toTypedArray()

        val builder = AlertDialog.Builder(activity)
            .setTitle("You have version $current")
            .setItems(labels) { _, which -> choose(activity, settings, list[which], current) }
            .setNegativeButton("Close", null)
        if (compare(latest.version, current) > 0) {
            builder.setPositiveButton("Install latest (${latest.version})") { _, _ ->
                choose(activity, settings, latest, current)
            }
        }
        builder.show()
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

    private fun start(activity: Activity, settings: Settings, r: Release) {
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
                download(r.apkUrl, file) { percent ->
                    activity.runOnUiThread { status.text = "Downloading ${r.version}... $percent%" }
                }
                activity.runOnUiThread { status.text = "Installing... confirm on the screen that appears" }
                install(activity, file)
                activity.runOnUiThread { status.postDelayed({ if (dialog.isShowing) dialog.dismiss() }, 4000) }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    Ui.toast(activity, "Update failed: ${e.message}")
                }
            }
        }.start()
    }
}
