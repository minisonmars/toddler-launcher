package com.toddlerlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Walks a parent through locking the device down. Each step shows a tick once
 * it is done, and its button opens the right Android settings page.
 */
class SetupActivity : Activity() {

    private lateinit var settings: Settings
    private val refreshers = mutableListOf<() -> Unit>()

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        val rows = Rows(this, col)

        rows.title("Lock-down checks")
        rows.note(
            "Work down the list. A green tick means a step is done. You can see these checks any time in " +
                "the parent area, under Protection and lock-down checks. The parent code starts as ${Settings.DEFAULT_PIN}."
        )

        refreshers += Checklist.addSteps(this, rows, settings) { refresh() }

        rows.header("Optional: screen pinning")
        rows.note(
            "Android's screen pinning locks the toddler screen in place, so even Home and Back do nothing. " +
                "Opening one of the allowed apps unpins it (Android only lets one app be pinned), and it pins " +
                "again when your child comes back. Android asks to confirm each time it pins, so this suits " +
                "a child who mostly uses the home screen itself. To use it, switch on Screen pinning (or App " +
                "pinning) in Android's security settings, and turn on \"Ask for PIN before unpinning\"."
        )
        rows.button("Open security settings") {
            settings.startParentMode()
            Device.openSecurity(this)
        }
        rows.switchRow("Pin the toddler screen", settings.pinScreen) { on ->
            settings.pinScreen = on
            on
        }

        rows.header("Finished?")
        rows.button("Go to the toddler screen") { finish() }

        setContentView(ScrollView(this).apply { addView(col) }.fitSystemBars())
    }

    override fun onResume() {
        super.onResume()
        settings.startParentMode()
        refresh()
    }

    @Deprecated("Framework callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && !Device.isDefaultHome(this)) settings.returnTo = "" // home app not chosen
        refresh()
    }

    private fun refresh() = refreshers.forEach { it() }

}
