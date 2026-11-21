package com.toddlerlauncher

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Parent screen for choosing which apps the toddler sees, and in what order. */
class AppPickerActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private var all = listOf<AppEntry>()

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(32))
        }
        scroll = ScrollView(this).apply { addView(list) }
        setContentView(scroll)
        all = Apps.launchable(this)
        build()
    }

    override fun onResume() {
        super.onResume()
        settings.startParentMode()
    }

    private fun build() {
        val y = scroll.scrollY
        list.removeAllViews()
        val rows = Rows(this, list)
        rows.title("Allowed apps")
        rows.note("Tick the apps your child can use. Use the arrows to change the order on the home screen.")

        val byPkg = all.associateBy { it.pkg }
        val chosen = settings.allowed.filter { byPkg.containsKey(it) }
        rows.header("On the home screen (${chosen.size})")
        if (chosen.isEmpty()) rows.note("None yet.")
        chosen.forEachIndexed { i, pkg ->
            addRow(byPkg.getValue(pkg), true, canUp = i > 0, canDown = i < chosen.size - 1)
        }

        rows.header("Other apps")
        for (app in all) {
            if (!chosen.contains(app.pkg)) addRow(app, false, canUp = false, canDown = false)
        }
        rows.button("Done") { finish() }
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun addRow(app: AppEntry, ticked: Boolean, canUp: Boolean, canDown: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(ImageView(this).apply { setImageDrawable(app.icon) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(TextView(this).apply {
            text = app.label
            textSize = 17f
            if (ticked) typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (ticked) {
            row.addView(arrow(Icon.ARROW_UP, canUp) { move(app.pkg, -1) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            row.addView(arrow(Icon.ARROW_DOWN, canDown) { move(app.pkg, 1) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        val box = CheckBox(this).apply {
            isChecked = ticked
            scaleX = 1.3f
            scaleY = 1.3f
            setOnCheckedChangeListener { _, on -> toggle(app.pkg, on) }
        }
        row.addView(box)
        row.setOnClickListener { box.isChecked = !box.isChecked }
        list.addView(row)
    }

    private fun arrow(icon: Icon, enabled: Boolean, onClick: () -> Unit) =
        RoundIconButton(this, icon, if (enabled) Ui.GREEN else Ui.GREY).apply {
            contentDescription = if (icon == Icon.ARROW_UP) "Move up" else "Move down"
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.3f
            setOnClickListener { onClick() }
        }

    private fun toggle(pkg: String, on: Boolean) {
        val now = settings.allowed.toMutableList()
        if (on && !now.contains(pkg)) now.add(pkg)
        if (!on) now.remove(pkg)
        settings.allowed = now
        list.post { build() }
    }

    private fun move(pkg: String, by: Int) {
        val installed = all.map { it.pkg }.toSet()
        val now = settings.allowed.filter { installed.contains(it) }.toMutableList()
        val i = now.indexOf(pkg)
        val j = i + by
        if (i < 0 || j !in now.indices) return
        now[i] = now[j].also { now[j] = now[i] }
        settings.allowed = now
        build()
    }
}
