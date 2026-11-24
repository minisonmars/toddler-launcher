package com.toddlerlauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A built-in number pad for the parent PIN, with dots showing how many digits
 * are in. The phone's own keyboard never opens.
 */
class PinPad(context: Context, keySizePx: Int) : LinearLayout(context) {

    var digits = ""
        private set

    /** Called after each change, with the digits so far. */
    var onChange: (String) -> Unit = {}
    /** Called when the tick is pressed. */
    var onEnter: (String) -> Unit = {}

    private val dots = Dots(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val gap = Ui.dp(context, 10)
        addView(dots, LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(context, 36)).apply { bottomMargin = gap })
        val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "ok"))
        for (row in keys) {
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (k in row) {
                val v: View = when (k) {
                    "<" -> RoundIconButton(context, Icon.ARROW_LEFT, Color.rgb(150, 150, 160)).apply {
                        contentDescription = "Delete"
                        setOnClickListener { press(this) { if (digits.isNotEmpty()) digits = digits.dropLast(1) } }
                        setOnLongClickListener { digits = ""; changed(); true }
                    }
                    "ok" -> RoundIconButton(context, Icon.CHECK, Ui.GREEN).apply {
                        contentDescription = "Enter"
                        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onEnter(digits) }
                    }
                    else -> key(k).apply { setOnClickListener { press(this) { if (digits.length < 8) digits += k } } }
                }
                line.addView(v, LayoutParams(keySizePx, keySizePx).apply { setMargins(gap, gap / 2, gap, gap / 2) })
            }
            addView(line)
        }
    }

    private fun key(label: String) = TextView(context).apply {
        text = label
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Ui.BLUE)
        }
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(90, 255, 255, 255)), shape, null)
        isClickable = true
        isFocusable = true
    }

    private fun press(v: View, change: () -> Unit) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        dots.error = false
        change()
        changed()
    }

    private fun changed() {
        dots.count = digits.length
        onChange(digits)
    }

    /** Shows the dots in red for a moment, then clears them. */
    fun showWrong() {
        dots.error = true
        dots.animate().translationX(Ui.dp(context, 12).toFloat()).setDuration(60).withEndAction {
            dots.animate().translationX(-Ui.dp(context, 12).toFloat()).setDuration(60).withEndAction {
                dots.animate().translationX(0f).setDuration(60).start()
            }.start()
        }.start()
        postDelayed({
            digits = ""
            dots.error = false
            changed()
        }, 600)
    }

    fun clear() {
        digits = ""
        changed()
    }

    /** One dot per digit typed, plus empty rings up to 4. */
    private class Dots(context: Context) : View(context) {
        var count = 0
            set(v) { field = v; invalidate() }
        var error = false
            set(v) { field = v; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val r = height * 0.22f
            val gap = r * 3.2f
            val shown = maxOf(4, count)
            val start = width / 2f - gap * (shown - 1) / 2f
            for (i in 0 until shown) {
                val cx = start + i * gap
                val colour = if (error) Ui.RED else Ui.BLUE
                if (i < count) {
                    paint.style = Paint.Style.FILL
                    paint.color = colour
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = r * 0.3f
                    paint.color = Color.rgb(150, 150, 160)
                }
                canvas.drawCircle(cx, height / 2f, r, paint)
            }
        }
    }
}
