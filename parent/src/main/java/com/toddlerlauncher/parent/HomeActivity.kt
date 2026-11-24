package com.toddlerlauncher.parent

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** The list of paired child devices. */
class HomeActivity : Activity() {

    private lateinit var rows: Rows
    private var update: Updater.Latest? = null
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
            rows.button("Download version ${u.version} of this app") {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
            }
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
            }
            rows.item(f?.name ?: c.name, where, if (f != null) Ui.GREEN else Ui.GREY) {
                startActivity(Intent(this, ChildActivity::class.java).putExtra("id", c.id))
            }
        }
        rows.button("Add a child's device") { startActivity(Intent(this, AddActivity::class.java)) }
        rows.note(
            "On the toddler device, remote access needs to be on (Parent area > Remote access, or the connect " +
                "button on the toddler screen). On the same Wi-Fi this app finds it by itself."
        )
        if (Direct.network != null) {
            rows.button("Leave the private Wi-Fi") {
                Direct.leave(this)
                build()
            }
        }
    }
}
