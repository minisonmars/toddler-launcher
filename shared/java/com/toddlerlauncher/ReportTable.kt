package com.toddlerlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The usage report as a table with lines between every row and column, so it's
 * easy to follow across. Shared by the launcher's parent area and the Toddler Parent app.
 */
object ReportTable {

    class Row(val cells: List<String>, val bold: Boolean = false, val shaded: Boolean = false)

    private val LINE = Color.argb(110, 128, 128, 128)
    private val SHADE = Color.argb(30, 128, 128, 128)

    fun build(ctx: Context, rows: List<Row>): View {
        val d = ctx.resources.displayMetrics.density
        val line = maxOf(1, (d + 0.5f).toInt())
        val table = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // The outside border
            background = GradientDrawable().apply { setStroke(line, LINE) }
            setPadding(line, line, line, line)
        }
        rows.forEachIndexed { r, row ->
            if (r > 0) table.addView(View(ctx).apply { setBackgroundColor(LINE) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, line))
            val tr = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                if (row.shaded || r == 0) setBackgroundColor(SHADE)
            }
            row.cells.forEachIndexed { i, text ->
                if (i > 0) tr.addView(View(ctx).apply { setBackgroundColor(LINE) },
                    LinearLayout.LayoutParams(line, ViewGroup.LayoutParams.MATCH_PARENT))
                tr.addView(TextView(ctx).apply {
                    this.text = text
                    textSize = 14f
                    if (row.bold || r == 0) typeface = Typeface.DEFAULT_BOLD
                    gravity = (if (i == 0) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL
                    maxLines = 2
                    val p = (6 * d).toInt()
                    setPadding(p, p, p, p)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, if (i == 0) 2.2f else 1f))
            }
            table.addView(tr, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return table
    }
}
