package com.toddlerlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** A 3 by 3 grid of dots. Drag a finger across them to draw a pattern. */
class PatternView(context: Context) : View(context) {

    /** Called when the finger lifts, with the dots in the order they were joined (0 = top left, 8 = bottom right). */
    var onDone: ((List<Int>) -> Unit)? = null

    private val chosen = mutableListOf<Int>()
    private var fingerX = -1f
    private var fingerY = -1f
    private var tracking = false
    var errorShown = false
        set(v) {
            field = v
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    private fun cell() = min(width, height) / 3f
    private fun left() = (width - cell() * 3) / 2f
    private fun top() = (height - cell() * 3) / 2f
    private fun cx(i: Int) = left() + cell() * (i % 3 + 0.5f)
    private fun cy(i: Int) = top() + cell() * (i / 3 + 0.5f)

    fun clear() {
        chosen.clear()
        errorShown = false
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    private fun dotAt(x: Float, y: Float): Int {
        for (i in 0 until 9) {
            if (hypot(x - cx(i), y - cy(i)) < cell() * 0.33f) return i
        }
        return -1
    }

    private fun add(i: Int) {
        if (i < 0 || chosen.contains(i)) return
        // Joining two dots in a straight line also picks the dot in between
        if (chosen.isNotEmpty()) {
            val last = chosen.last()
            val mid = if ((last % 3 + i % 3) % 2 == 0 && (last / 3 + i / 3) % 2 == 0) (last + i) / 2 else -1
            if (mid in 0..8 && mid != last && mid != i && !chosen.contains(mid)) chosen.add(mid)
        }
        chosen.add(i)
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        fingerX = event.x
        fingerY = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                chosen.clear()
                errorShown = false
                tracking = true
                add(dotAt(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> if (tracking) add(dotAt(event.x, event.y))
            MotionEvent.ACTION_UP -> {
                tracking = false
                if (chosen.isNotEmpty()) onDone?.invoke(chosen.toList())
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                chosen.clear()
            }
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val c = cell()
        val lineColour = if (errorShown) Ui.RED else Ui.GREEN
        paint.color = lineColour
        paint.strokeWidth = c * 0.08f
        for (k in 1 until chosen.size) {
            canvas.drawLine(cx(chosen[k - 1]), cy(chosen[k - 1]), cx(chosen[k]), cy(chosen[k]), paint)
        }
        if (tracking && chosen.isNotEmpty()) {
            canvas.drawLine(cx(chosen.last()), cy(chosen.last()), fingerX, fingerY, paint)
        }
        for (i in 0 until 9) {
            val on = chosen.contains(i)
            paint.color = if (on) lineColour else Color.rgb(170, 170, 170)
            canvas.drawCircle(cx(i), cy(i), if (on) c * 0.13f else c * 0.08f, paint)
        }
    }
}
