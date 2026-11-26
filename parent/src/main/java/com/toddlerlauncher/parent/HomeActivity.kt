package com.toddlerlauncher.parent

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** The list of paired child devices. */
class HomeActivity : Activity() {

    private lateinit var rows: Rows
    private var update: Updater.Release? = null
    private val onFound: () -> Unit = { build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rows = Rows(this, Ui.page(this))
        Thread {
            val u = Updater.newer(this)
            runOnUiThread {
                update = u
                if (!isFinishing) build()
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        Finder.start(this)
        Finder.watch(onFound)
        build()
    }

    override fun onPause() {
        Finder.unwatch(onFound)
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) Finder.stop()
        super.onDestroy()
    }

    private fun build() {
        rows.clear()
        rows.title("Toddler Parent", "Version ${Updater.installed(this)}")
        update?.let { u ->
            rows.note("Version ${u.version} of this app is ready", Ui.ORANGE, 16f)
            rows.button("See what's new and update") { Updater.showDialog(this) }
        }
        rows.header("Your child's devices")
        val children = Store.list(this)
        if (children.isEmpty()) {
            rows.note("None yet. Tap \"Add a child's device\" below.")
        }
        for (c in children) {
            val f = Finder.found[c.id]
            val where = when {
                f != null -> "On this Wi-Fi"
                Direct.network != null && Direct.ssid == c.ssid && c.ssid.isNotEmpty() -> "Connected directly"
                else -> "Not found on this Wi-Fi right now (tap to try anyway)"
            } + if (c.routes.size > 1) "  ·  ${c.routes.size} ways to connect" else ""
            rows.item(f?.name ?: c.name, where, if (f != null) Ui.GREEN else Ui.GREY) {
                startActivity(Intent(this, ChildActivity::class.java).putExtra("id", c.id))
            }
        }
        rows.button("Add a child's device") { startActivity(Intent(this, AddActivity::class.java)) }
        rows.note(
            "On the toddler device, remote access needs to be on (Parent area > Remote access, or the connect " +
                "button on the toddler screen). On the same Wi-Fi this app finds it by itself."
        )
        rows.header("This app")
        rows.button("App updates") { Updater.showDialog(this) }
        if (Direct.network != null) {
            rows.button("Leave the private Wi-Fi") {
                Direct.leave(this)
                build()
            }
        }
    }
}
