package com.toddlerlauncher.parent

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

object Ui {
    val GREEN = Color.rgb(52, 179, 122)
    val PURPLE = Color.rgb(140, 90, 230)
    val RED = Color.rgb(225, 60, 60)
    val ORANGE = Color.rgb(255, 150, 30)
    val GREY = Color.rgb(140, 140, 140)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()

    /** A scrolling column as the whole screen. */
    fun page(activity: Activity): LinearLayout {
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 20), dp(activity, 20), dp(activity, 40))
        }
        activity.setContentView(ScrollView(activity).apply {
            isFillViewport = true
            addView(col)
        }.fitSystemBars())
        return col
    }
}

/** Builds simple rows of settings into a column. */
class Rows(private val activity: Activity, val col: LinearLayout) {
    private fun dp(v: Int) = Ui.dp(activity, v)

    fun clear() = col.removeAllViews()

    fun title(text: String, sub: String = "") {
        col.addView(TextView(activity).apply {
            this.text = text
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        })
        if (sub.isNotEmpty()) note(sub)
    }

    fun header(text: String) {
        col.addView(TextView(activity).apply {
            this.text = text
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.PURPLE)
            setPadding(0, dp(26), 0, dp(4))
        })
    }

    fun note(text: String, colour: Int = Ui.GREY, size: Float = 14f): TextView {
        val tv = TextView(activity).apply {
            this.text = text
            textSize = size
            setTextColor(colour)
            setPadding(0, dp(2), 0, dp(6))
        }
        col.addView(tv)
        return tv
    }

    fun big(text: String): TextView = note(text, Ui.GREEN, 20f).apply { typeface = Typeface.DEFAULT_BOLD }

    fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 16f
            setOnClickListener { onClick() }
        }
        col.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return b
    }

    /** Several buttons side by side. */
    fun buttons(vararg items: Pair<String, () -> Unit>) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        for ((label, click) in items) {
            row.addView(Button(activity).apply {
                text = label
                isAllCaps = false
                textSize = 15f
                setOnClickListener { click() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(row)
    }

    fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): Switch {
        val sw = Switch(activity).apply {
            text = label
            textSize = 16f
            isChecked = checked
            setPadding(0, dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, on -> onChange(on) }
        }
        col.addView(sw)
        return sw
    }

    fun choiceRow(label: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit): Spinner {
        if (label.isNotEmpty()) col.addView(TextView(activity).apply {
            text = label
            textSize = 16f
            setPadding(0, dp(10), 0, 0)
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

    /** A parent area menu line: round icon, title and a smaller line (red when it needs attention). Same as the launcher's. */
    fun menuItem(title: String, subtitle: String, warn: Boolean, icon: com.toddlerlauncher.Icon, colour: Int, onClick: () -> Unit) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
            isClickable = true
            val ta = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = ta.getDrawable(0)
            ta.recycle()
            setOnClickListener { onClick() }
        }
        row.addView(com.toddlerlauncher.RoundIconButton(activity, icon, if (warn) Ui.RED else colour).apply {
            isClickable = false
            isFocusable = false
            isDuplicateParentStateEnabled = true
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(0, 0, dp(14), 0) })
        val text = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), dp(4), dp(16))
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        text.addView(TextView(activity).apply {
            this.text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        })
        text.addView(TextView(activity).apply {
            this.text = subtitle
            textSize = 14f
            setTextColor(if (warn) Ui.RED else Ui.GREY)
        })
        col.addView(row)
        col.addView(View(activity).apply { setBackgroundColor(Color.argb(40, 128, 128, 128)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    /** A tick or cross with a label, like the lock-down checks. */
    fun check(title: String, done: Boolean) {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(com.toddlerlauncher.RoundIconButton(activity,
            if (done) com.toddlerlauncher.Icon.CHECK else com.toddlerlauncher.Icon.CROSS, if (done) Ui.GREEN else Ui.RED).apply {
            isClickable = false
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(0, 0, dp(12), 0) })
        row.addView(TextView(activity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(row)
    }

    /** A tappable line: bold title with a smaller line under it. */
    fun item(title: String, subtitle: String, subColour: Int = Ui.GREY, onClick: () -> Unit) {
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(14), dp(4), dp(14))
            isClickable = true
            val ta = activity.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = ta.getDrawable(0)
            ta.recycle()
            setOnClickListener { onClick() }
        }
        box.addView(TextView(activity).apply {
            text = title
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(activity).apply {
            text = subtitle
            textSize = 14f
            setTextColor(subColour)
        })
        col.addView(box)
        col.addView(View(activity).apply { setBackgroundColor(Color.argb(40, 128, 128, 128)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }
}
