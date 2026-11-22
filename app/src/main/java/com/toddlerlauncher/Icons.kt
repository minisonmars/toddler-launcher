package com.toddlerlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

enum class Icon { NONE, GEAR, ARROW_LEFT, ARROW_RIGHT, ARROW_UP, ARROW_DOWN, CLOSE, DOWNLOAD, CHECK, CROSS, PAUSE, MOON, HOURGLASS, REMOTE }

/**
 * A round button with an icon drawn in code, so every icon has the same size,
 * weight and alignment on every phone (emoji and symbol characters don't).
 */
open class RoundIconButton(context: Context, icon: Icon, fill: Int) : View(context) {

    var icon: Icon = icon
        set(value) {
            field = value
            invalidate()
        }
    var fill: Int = fill
        set(value) {
            field = value
            invalidate()
        }

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    init {
        isFocusable = true
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width, height).toFloat()
        val c = s / 2f
        canvas.save()
        if (isPressed) canvas.scale(0.92f, 0.92f, c, c)

        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawCircle(c, c, c - s * 0.04f, paint)

        canvas.save()
        canvas.scale(s, s)
        drawIcon(canvas)
        canvas.restore()
        canvas.restore()
    }

    private fun fillStyle() {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
    }

    private fun strokeStyle(width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = Color.WHITE
    }

    private fun drawIcon(canvas: Canvas) {
        when (icon) {
            Icon.NONE -> {}

            Icon.GEAR -> {
                fillStyle()
                for (i in 0 until 8) {
                    canvas.save()
                    canvas.rotate(i * 45f, 0.5f, 0.5f)
                    rect.set(0.44f, 0.16f, 0.56f, 0.31f)
                    canvas.drawRoundRect(rect, 0.02f, 0.02f, paint)
                    canvas.restore()
                }
                strokeStyle(0.09f)
                canvas.drawCircle(0.5f, 0.5f, 0.19f, paint)
            }

            Icon.ARROW_LEFT, Icon.ARROW_RIGHT, Icon.ARROW_UP, Icon.ARROW_DOWN -> {
                when (icon) {
                    Icon.ARROW_RIGHT -> canvas.scale(-1f, 1f, 0.5f, 0.5f)
                    Icon.ARROW_UP -> canvas.rotate(90f, 0.5f, 0.5f)
                    Icon.ARROW_DOWN -> canvas.rotate(-90f, 0.5f, 0.5f)
                    else -> {}
                }
                strokeStyle(0.10f)
                path.reset()
                path.moveTo(0.76f, 0.50f)
                path.lineTo(0.26f, 0.50f)
                path.moveTo(0.48f, 0.28f)
                path.lineTo(0.26f, 0.50f)
                path.lineTo(0.48f, 0.72f)
                canvas.drawPath(path, paint)
            }

            Icon.CLOSE, Icon.CROSS -> {
                strokeStyle(0.10f)
                canvas.drawLine(0.32f, 0.32f, 0.68f, 0.68f, paint)
                canvas.drawLine(0.68f, 0.32f, 0.32f, 0.68f, paint)
            }

            Icon.CHECK -> {
                strokeStyle(0.10f)
                path.reset()
                path.moveTo(0.27f, 0.52f)
                path.lineTo(0.43f, 0.68f)
                path.lineTo(0.74f, 0.35f)
                canvas.drawPath(path, paint)
            }

            Icon.DOWNLOAD -> {
                strokeStyle(0.09f)
                path.reset()
                path.moveTo(0.5f, 0.22f)
                path.lineTo(0.5f, 0.60f)
                path.moveTo(0.33f, 0.45f)
                path.lineTo(0.5f, 0.62f)
                path.lineTo(0.67f, 0.45f)
                canvas.drawPath(path, paint)
                canvas.drawLine(0.28f, 0.76f, 0.72f, 0.76f, paint)
            }

            Icon.REMOTE -> {
                // A phone with signal waves
                strokeStyle(0.06f)
                rect.set(0.24f, 0.30f, 0.50f, 0.76f)
                canvas.drawRoundRect(rect, 0.05f, 0.05f, paint)
                rect.set(0.50f, 0.22f, 0.86f, 0.58f)
                canvas.drawArc(rect, -60f, 60f, false, paint)
                rect.set(0.42f, 0.14f, 0.94f, 0.66f)
                canvas.drawArc(rect, -60f, 60f, false, paint)
            }

            Icon.MOON -> {
                fillStyle()
                path.reset()
                path.addCircle(0.47f, 0.5f, 0.26f, Path.Direction.CW)
                val bite = Path().apply { addCircle(0.60f, 0.42f, 0.22f, Path.Direction.CW) }
                path.op(bite, Path.Op.DIFFERENCE)
                canvas.drawPath(path, paint)
                canvas.drawCircle(0.70f, 0.70f, 0.035f, paint)
                canvas.drawCircle(0.78f, 0.30f, 0.025f, paint)
            }

            Icon.HOURGLASS -> {
                strokeStyle(0.07f)
                canvas.drawLine(0.30f, 0.22f, 0.70f, 0.22f, paint)
                canvas.drawLine(0.30f, 0.78f, 0.70f, 0.78f, paint)
                path.reset()
                path.moveTo(0.35f, 0.24f)
                path.lineTo(0.65f, 0.24f)
                path.lineTo(0.50f, 0.50f)
                path.lineTo(0.65f, 0.76f)
                path.lineTo(0.35f, 0.76f)
                path.lineTo(0.50f, 0.50f)
                path.close()
                paint.strokeWidth = 0.05f
                canvas.drawPath(path, paint)
                fillStyle()
                path.reset()
                path.moveTo(0.40f, 0.74f)
                path.lineTo(0.60f, 0.74f)
                path.lineTo(0.50f, 0.60f)
                path.close()
                canvas.drawPath(path, paint)
            }

            Icon.PAUSE -> {
                fillStyle()
                rect.set(0.33f, 0.30f, 0.45f, 0.70f)
                canvas.drawRoundRect(rect, 0.03f, 0.03f, paint)
                rect.set(0.55f, 0.30f, 0.67f, 0.70f)
                canvas.drawRoundRect(rect, 0.03f, 0.03f, paint)
            }
        }
    }
}

/**
 * The parent button: it only works when held down for [holdMs]. A ring fills
 * up around it while it is held, so a parent can see it working.
 */
@SuppressLint("ViewConstructor")
class HoldButton(context: Context, icon: Icon, fill: Int) : RoundIconButton(context, icon, fill) {

    var holdMs = 3000L
    var onHeld: (() -> Unit)? = null

    private var downAt = 0L
    private var holding = false
    private val arc = RectF()

    private val tick = object : Runnable {
        override fun run() {
            if (!holding) return
            if (SystemClock.uptimeMillis() - downAt >= holdMs) {
                holding = false
                isPressed = false
                invalidate()
                onHeld?.invoke()
            } else {
                invalidate()
                postOnAnimation(this)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = SystemClock.uptimeMillis()
                holding = true
                isPressed = true
                postOnAnimation(tick)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.x < 0 || event.y < 0 || event.x > width || event.y > height) stop()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stop()
        }
        return true
    }

    private fun stop() {
        holding = false
        isPressed = false
        removeCallbacks(tick)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!holding) return
        val s = min(width, height).toFloat()
        val fraction = ((SystemClock.uptimeMillis() - downAt).toFloat() / holdMs).coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.08f
        paint.color = Color.rgb(255, 210, 60)
        val inset = s * 0.05f
        arc.set(inset, inset, s - inset, s - inset)
        canvas.drawArc(arc, -90f, 360f * fraction, false, paint)
    }
}

/** A row of dots showing which page of apps is on screen. */
class PageDots(context: Context) : View(context) {
    var count = 1
        set(v) {
            field = v
            invalidate()
        }
    var current = 0
        set(v) {
            field = v
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        if (count <= 1) return
        val r = height * 0.22f
        val gap = r * 3.2f
        val start = width / 2f - gap * (count - 1) / 2f
        for (i in 0 until count) {
            paint.color = if (i == current) Color.WHITE else Color.argb(110, 255, 255, 255)
            canvas.drawCircle(start + i * gap, height / 2f, if (i == current) r * 1.25f else r, paint)
        }
    }
}
