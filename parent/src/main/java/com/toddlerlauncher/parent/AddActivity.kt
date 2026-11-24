package com.toddlerlauncher.parent

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import com.toddlerlauncher.link.Link

/** Finds a child device (on the Wi-Fi, by QR code, or by address) and pairs with it. */
class AddActivity : Activity() {

    private lateinit var rows: Rows
    private val onFound: () -> Unit = { build() }
    /** The private hotspot, if the QR code was for one, saved with the child once paired. */
    private var direct: Triple<String, String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rows = Rows(this, Ui.page(this))
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

    private fun build() {
        rows.clear()
        rows.title("Add a child's device")
        rows.header("Found on this Wi-Fi")
        val found = Finder.found.values.sortedBy { it.name.lowercase() }
        if (found.isEmpty()) rows.note("Looking... Make sure remote access is on on the toddler device. " +
            "If nothing shows up after a few seconds, your router may block this: scan the QR code instead.")
        val paired = Store.list(this).map { it.id }.toSet()
        for (f in found) {
            rows.item(f.name, if (f.id in paired) "Already paired. Tap to pair again" else "Tap to pair", Ui.GREEN) {
                direct = null
                askPin(f.host, f.port, f.name)
            }
        }
        rows.header("Or")
        rows.button("Scan the QR code") {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, ScanActivity::class.java), SCAN)
        }
        rows.note("On the toddler device, tap the connect button at the top of the screen, or go to Parent area > Remote access.")
        rows.button("Type the address") { askAddress() }
        rows.header("No Wi-Fi?")
        rows.note(
            "On the toddler device, tap the connect button, then \"No Wi-Fi? Connect directly\". It makes a private " +
                "Wi-Fi and shows a QR code: scan it here. Android asks you to confirm joining it. Your phone keeps " +
                "its mobile data for everything else."
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCAN && resultCode == RESULT_OK) handleCode(data?.getStringExtra("text") ?: "")
    }

    private fun handleCode(text: String) {
        val uri = try { Uri.parse(text) } catch (e: Exception) { null }
        when {
            uri?.scheme == "toddlerlink" && uri.host == "direct" -> {
                val ssid = uri.getQueryParameter("ssid") ?: ""
                val pass = uri.getQueryParameter("pass") ?: ""
                val sec = uri.getQueryParameter("sec") ?: "wpa2"
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: Link.PORT
                val hosts = uri.getQueryParameters("host")
                val wait = progress("Joining the toddler device's private Wi-Fi...")
                Direct.join(this, ssid, pass, sec, hosts) { problem ->
                    if (problem != null) {
                        wait.dismiss()
                        message("Couldn't connect", problem)
                        return@join
                    }
                    Thread {
                        // Find which of its addresses answers over the hotspot
                        var hit: Pair<String, String>? = null
                        for (attempt in 0 until 5) {
                            for (h in hosts) {
                                val hi = Net.hello(h, port)
                                if (hi != null) { hit = h to hi.optString("name"); break }
                            }
                            if (hit != null) break
                            Thread.sleep(1500)
                        }
                        runOnUiThread {
                            wait.dismiss()
                            val (h, name) = hit ?: return@runOnUiThread message("Couldn't connect",
                                "Joined the private Wi-Fi but the toddler device didn't answer. Close its QR code, tap Connect directly again and rescan.")
                            direct = Triple(ssid, pass, sec)
                            askPin(h, port, name)
                        }
                    }.start()
                }
            }
            uri?.scheme == "http" && uri.host != null -> {
                direct = null
                checkThenPair(uri.host!!, if (uri.port > 0) uri.port else Link.PORT)
            }
            else -> message("Not a Toddler Launcher code", "That QR code isn't one from Toddler Launcher.")
        }
    }

    private fun askAddress() {
        val field = EditText(this).apply {
            hint = "For example 192.168.1.23"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this).setTitle("Address of the toddler device")
            .setMessage("Shown in its Parent area > Remote access.")
            .setView(FrameLayout(this).apply {
                setPadding(Ui.dp(this@AddActivity, 24), 0, Ui.dp(this@AddActivity, 24), 0)
                addView(field)
            })
            .setPositiveButton("Next") { _, _ ->
                val t = field.text.toString().trim().removePrefix("http://").removeSuffix("/")
                val host = t.substringBefore(':')
                val port = t.substringAfter(':', "").substringBefore('/').toIntOrNull() ?: Link.PORT
                if (host.isNotEmpty()) {
                    direct = null
                    checkThenPair(host, port)
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun checkThenPair(host: String, port: Int) {
        val wait = progress("Looking for the toddler device...")
        Thread {
            val hi = Net.hello(host, port)
            runOnUiThread {
                wait.dismiss()
                if (hi == null) message("Couldn't reach it", "Nothing answered at $host. Check both devices are on the same Wi-Fi and remote access is on.")
                else askPin(host, port, hi.optString("name"))
            }
        }.start()
    }

    private fun askPin(host: String, port: Int, name: String) {
        val field = EditText(this).apply {
            hint = "Parent PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this).setTitle("Pair with $name")
            .setMessage("Enter the parent PIN of the toddler device. You only need it this once.")
            .setView(FrameLayout(this).apply {
                setPadding(Ui.dp(this@AddActivity, 24), 0, Ui.dp(this@AddActivity, 24), 0)
                addView(field)
            })
            .setPositiveButton("Pair") { _, _ -> pair(host, port, field.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun pair(host: String, port: Int, pin: String) {
        val wait = progress("Pairing...")
        val hotspot = direct
        Thread {
            val result = try {
                val (child, code) = Net.pair(this, host, port, pin)
                if (hotspot != null) {
                    child.ssid = hotspot.first
                    child.pass = hotspot.second
                    child.sec = hotspot.third
                    Store.save(this, child)
                }
                Triple(child, code, null as String?)
            } catch (e: LinkError) {
                Triple(null, "", e.message)
            } catch (e: Exception) {
                Triple(null, "", "Pairing failed: ${e.message}")
            }
            runOnUiThread {
                wait.dismiss()
                val (child, code, problem) = result
                if (child == null) {
                    message("Not paired", problem ?: "")
                } else {
                    Ui.toast(this, "Paired with ${child.name}. Check code $code shows on the toddler device too.")
                    startActivity(Intent(this, ChildActivity::class.java).putExtra("id", child.id))
                    finish()
                }
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun progress(text: String): ProgressDialog = ProgressDialog(this).apply {
        setMessage(text)
        setCancelable(false)
        show()
    }

    private fun message(title: String, text: String) {
        if (isFinishing) return
        AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("OK", null).show()
    }

    companion object {
        private const val SCAN = 1
    }
}
