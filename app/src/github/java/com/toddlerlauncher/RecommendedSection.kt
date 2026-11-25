package com.toddlerlauncher

import android.app.Activity

/**
 * The "Recommended apps" section of the parent area (GitHub version only): Ben's other
 * toddler apps, listed in catalog.json, installed and updated straight from GitHub.
 */
class RecommendedSection(private val a: Activity, private val settings: Settings, private val rebuild: () -> Unit) {

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
            a.runOnUiThread {
                catalogLoading = false
                catalog = list
                latest = found
                catalogError = error
                rebuild()
            }
        }.start()
    }

    fun build(rows: Rows) {
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
            val have = Catalog.installedVersion(a, e.pkg)
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
                    Updater.start(a, settings, newest, e.name)
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
                    if (!Apps.launch(a, e.pkg)) Ui.toast(a, "Couldn't open ${e.name}")
                }
            }
        }
        rows.button("Check again") {
            catalog = null
            rebuild()
        }
    }
}
