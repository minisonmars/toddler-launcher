package com.toddlerlauncher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object Ui {
    val GREEN = Color.rgb(52, 179, 122)
    val BLUE = Color.rgb(50, 130, 255)
    val ORANGE = Color.rgb(255, 150, 30)
    val RED = Color.rgb(225, 60, 60)
    val GREY = Color.rgb(140, 140, 140)
    val SHADE = Color.argb(90, 0, 0, 0)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
}

/** Builds the simple scrolling lists used by the parent area and setup screens. */
class Rows(private val activity: Activity, val col: LinearLayout) {

    private fun dp(v: Int) = Ui.dp(activity, v)

    fun title(text: String) {
        col.addView(TextView(activity).apply {
            this.text = text
            textSize = 26f
            setPadding(0, 0, 0, dp(4))
        })
    }

    fun header(text: String) {
        col.addView(TextView(activity).apply {
            this.text = text
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.GREEN)
            setPadding(0, dp(28), 0, dp(4))
        })
    }

    fun note(text: String): TextView {
        val tv = TextView(activity).apply {
            this.text = text
            textSize = 14f
            setTextColor(Ui.GREY)
            setPadding(0, dp(2), 0, dp(6))
        }
        col.addView(tv)
        return tv
    }

    /** onChange returns the state the switch should end up in. */
    fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Boolean): Switch {
        val sw = Switch(activity).apply {
            text = label
            textSize = 16f
            isChecked = checked
            setPadding(0, dp(12), 0, dp(12))
        }
        lateinit var listener: (View, Boolean) -> Unit
        listener = { v, on ->
            val result = onChange(on)
            if (result != on) {
                val s = v as Switch
                s.setOnCheckedChangeListener(null)
                s.isChecked = result
                s.setOnCheckedChangeListener { b, o -> listener(b, o) }
            }
        }
        sw.setOnCheckedChangeListener { b, o -> listener(b, o) }
        col.addView(sw)
        return sw
    }

    fun choiceRow(
        label: String,
        options: List<Pair<String, String>>,
        current: String,
        onPick: (String) -> Unit
    ): Spinner {
        col.addView(TextView(activity).apply {
            text = label
            textSize = 16f
            setPadding(0, dp(12), 0, 0)
        })
        val spinner = Spinner(activity)
        val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, options.map { it.second })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val start = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        spinner.setSelection(start)
        var shown = start
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != shown) {
                    shown = position
                    onPick(options[position].first)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(spinner)
        return spinner
    }

    fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setOnClickListener { onClick() }
        }
        col.addView(
            b,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        return b
    }

    /** A big tappable line in a menu: a title and a smaller line underneath (red when it needs attention). */
    fun menuItem(title: String, subtitle: String, warn: Boolean = false, onClick: () -> Unit) {
        val item = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(16), dp(4), dp(16))
            isClickable = true
            isFocusable = true
            val ta = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = ta.getDrawable(0)
            ta.recycle()
            setOnClickListener { onClick() }
        }
        item.addView(TextView(activity).apply {
            text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        })
        item.addView(TextView(activity).apply {
            text = subtitle
            textSize = 14f
            setTextColor(if (warn) Ui.RED else Ui.GREY)
        })
        col.addView(item)
        col.addView(View(activity).apply { setBackgroundColor(Color.argb(40, 128, 128, 128)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    /** A setup step: tick or cross, title, explanation and a button. Returns a function that refreshes it. */
    fun step(title: String, explain: String, buttonLabel: String, isDone: () -> Boolean, onClick: () -> Unit): () -> Unit {
        val mark = RoundIconButton(activity, Icon.CROSS, Ui.RED).apply { isClickable = false; isFocusable = false }
        val name = TextView(activity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        }
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(24), 0, dp(4))
            addView(mark, LinearLayout.LayoutParams(dp(36), dp(36)))
            addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(top)
        note(explain)
        button(buttonLabel, onClick)
        val refresh = {
            val done = isDone()
            mark.icon = if (done) Icon.CHECK else Icon.CROSS
            mark.fill = if (done) Ui.GREEN else Ui.RED
        }
        refresh()
        return refresh
    }
}
