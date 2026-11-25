package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Updates for the Google Play version. Google Play does the updating: this only asks Play
 * whether a newer version is out (Play's In-App Updates) and opens Play's own update screen.
 * "What's new" comes from CHANGELOG.md, which is built into the app.
 */
object Updater {

    private const val REQUEST_UPDATE = 501

    fun installedVersion(ctx: Context): String =
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
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

    /**
     * Play only gives the new version's build number. Ours are "0.1.<build number>", so the
     * version name is the installed one with its last part swapped for the new build number.
     */
    private fun versionFor(ctx: Context, code: Int): String {
        val parts = installedVersion(ctx).split('.')
        return (parts.dropLast(1) + code.toString()).joinToString(".")
    }

    // ---------- Automatic update check ----------

    private const val CHECK_EVERY_MS = 6 * 60 * 60_000L
    @Volatile private var checking = false

    /** Quietly asks Google Play at most every 6 hours (or now if [force]); the answer is saved in settings. */
    fun checkInBackground(ctx: Context, force: Boolean = false, done: (() -> Unit)? = null) {
        val settings = Settings(ctx)
        val now = System.currentTimeMillis()
        if (checking || (!force && now - settings.updateCheckedAt < CHECK_EVERY_MS)) return
        checking = true
        val app = ctx.applicationContext
        try {
            AppUpdateManagerFactory.create(app).appUpdateInfo
                .addOnSuccessListener { info ->
                    settings.updateCheckedAt = System.currentTimeMillis()
                    val v = if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE)
                        versionFor(app, info.availableVersionCode()) else ""
                    if (v != settings.latestVersion) settings.latestVersion = v
                    checking = false
                    done?.invoke()
                }
                .addOnFailureListener {
                    // Not installed from Play, no internet, or Play busy: try again next time
                    checking = false
                }
        } catch (e: Exception) {
            checking = false
        }
    }

    /** The newer version waiting on Google Play, or null. */
    fun available(ctx: Context): String? {
        val v = Settings(ctx).latestVersion
        return if (v.isNotEmpty() && compare(v, installedVersion(ctx)) > 0) v else null
    }

    // ---------- What's new (CHANGELOG.md built into the app) ----------

    class Section(val version: String, val notes: String)

    /** Every version in the built-in changelog, newest first. "Next" is this build itself. */
    private fun changelog(ctx: Context): List<Section> {
        val text = try {
            ctx.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return emptyList()
        }
        val out = mutableListOf<Section>()
        val parts = Regex("^## (.+)$", RegexOption.MULTILINE).split(text)
        val titles = Regex("^## (.+)$", RegexOption.MULTILINE).findAll(text).map { it.groupValues[1].trim() }.toList()
        for ((i, title) in titles.withIndex()) {
            val body = parts.getOrElse(i + 1) { "" }.lines()
                .filter { it.isNotBlank() && it.trim() != "- (nothing yet)" }
                .joinToString("\n") { it.trim().removePrefix("- ").let { b -> "• $b" } }
            if (body.isEmpty()) continue
            val v = if (title == "Next") installedVersion(ctx) else title.removePrefix("v")
            if (out.none { it.version == v }) out.add(Section(v, body))
        }
        return out
    }

    // ---------- The updates screen ----------

    /** Shows the installed version and what's new, and offers Google Play's update when there is one. */
    fun showDialog(activity: Activity, settings: Settings) {
        settings.startParentMode()
        fun dp(v: Int) = Ui.dp(activity, v)
        fun text(s: String, size: Float, bold: Boolean = false, colour: Int? = null) = TextView(activity).apply {
            text = s
            textSize = size
            if (bold) typeface = Typeface.DEFAULT_BOLD
            if (colour != null) setTextColor(colour)
            setPadding(0, dp(4), 0, dp(4))
        }
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        lateinit var dialog: AlertDialog
        val current = installedVersion(activity)

        // The top part: filled in once Google Play answers
        val status = text("Checking Google Play for a new version...", 16f)
        col.addView(status)
        val updateButton = Button(activity).apply {
            isAllCaps = false
            visibility = View.GONE
        }
        col.addView(updateButton)

        val log = changelog(activity)
        val installed = log.firstOrNull { it.version == current }
        col.addView(text("Installed: version $current", 18f, bold = true, colour = Ui.GREEN)
            .apply { setPadding(0, dp(18), 0, dp(4)) })
        col.addView(text(installed?.notes ?: "No notes for this version.", 14f))

        val older = log.filter { compare(it.version, current) < 0 }
        if (older.isNotEmpty()) {
            // Older versions stay out of the way until asked for
            val olderBox = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            val showOlder = Button(activity).apply {
                text = "Show older versions (${older.size})"
                isAllCaps = false
            }
            showOlder.setOnClickListener {
                showOlder.visibility = View.GONE
                olderBox.visibility = View.VISIBLE
            }
            col.addView(showOlder)
            col.addView(olderBox)
            for (s in older) {
                olderBox.addView(text("Version ${s.version}", 16f, bold = true).apply { setPadding(0, dp(14), 0, 0) })
                olderBox.addView(text(s.notes, 14f, colour = Ui.GREY))
            }
        }

        dialog = AlertDialog.Builder(activity)
            .setTitle("App updates")
            .setView(ScrollView(activity).apply { addView(col) })
            .setNegativeButton("Close", null)
            .create()
        dialog.show()

        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (activity.isFinishing || !dialog.isShowing) return@addOnSuccessListener
                settings.updateCheckedAt = System.currentTimeMillis()
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    val v = versionFor(activity, info.availableVersionCode())
                    settings.latestVersion = v
                    status.text = "Version $v is on Google Play."
                    status.setTypeface(null, Typeface.BOLD)
                    updateButton.text = "Update to version $v"
                    updateButton.visibility = View.VISIBLE
                    updateButton.setOnClickListener {
                        dialog.dismiss()
                        startUpdate(activity, settings, info)
                    }
                } else {
                    settings.latestVersion = ""
                    status.text = "You have the latest version."
                }
            }
            .addOnFailureListener {
                if (activity.isFinishing || !dialog.isShowing) return@addOnFailureListener
                status.text = "Couldn't ask Google Play just now. Updates come from Google Play: you can also check there."
                updateButton.text = "Open Google Play"
                updateButton.visibility = View.VISIBLE
                updateButton.setOnClickListener {
                    dialog.dismiss()
                    openPlay(activity, settings)
                }
            }
    }

    /** Opens Google Play's own update screen; Play downloads, installs and restarts the app. */
    private fun startUpdate(activity: Activity, settings: Settings, info: AppUpdateInfo) {
        Device.unpin(activity) // screen pinning would block Play's update screen
        settings.startParentMode()
        // Toddler Guard stands aside while Play updates (until the new version starts, or 5 minutes)
        settings.installingUntil = System.currentTimeMillis() + 5 * 60_000L
        settings.installingFrom = installedVersion(activity)
        try {
            if (!info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) throw IllegalStateException("not allowed")
            AppUpdateManagerFactory.create(activity).startUpdateFlowForResult(
                info, activity, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE), REQUEST_UPDATE
            )
        } catch (e: Exception) {
            openPlay(activity, settings)
        }
    }

    private fun openPlay(activity: Activity, settings: Settings) {
        settings.startParentMode()
        val id = activity.packageName
        if (!Device.open(activity, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))) {
            Device.open(activity, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id")))
        }
    }
}
