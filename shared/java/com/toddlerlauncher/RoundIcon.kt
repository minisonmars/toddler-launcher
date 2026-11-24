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

enum class Icon { NONE, GEAR, ARROW_LEFT, ARROW_RIGHT, ARROW_UP, ARROW_DOWN, CLOSE, DOWNLOAD, CHECK, CROSS, PAUSE, MOON, HOURGLASS, REMOTE,
    CHECKLIST, GRID, STAR, CLOCK, CHART, PALETTE, LOCK, SHIELD, WIFI, DEVICE, QR }

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

            Icon.CHECKLIST -> {
                strokeStyle(0.06f)
                rect.set(0.26f, 0.20f, 0.74f, 0.82f)
                canvas.drawRoundRect(rect, 0.05f, 0.05f, paint)
                for (k in 0 until 3) {
                    val y = 0.36f + k * 0.15f
                    path.reset()
                    path.moveTo(0.33f, y)
                    path.lineTo(0.37f, y + 0.04f)
                    path.lineTo(0.44f, y - 0.04f)
                    canvas.drawPath(path, paint)
                    canvas.drawLine(0.50f, y, 0.66f, y, paint)
                }
            }

            Icon.GRID -> {
                fillStyle()
                for (r in 0 until 2) for (c in 0 until 2) {
                    rect.set(0.24f + c * 0.28f, 0.24f + r * 0.28f, 0.48f + c * 0.28f, 0.48f + r * 0.28f)
                    canvas.drawRoundRect(rect, 0.05f, 0.05f, paint)
                }
            }

            Icon.STAR -> {
                fillStyle()
                path.reset()
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 0.30f else 0.13f
                    val a = Math.toRadians((-90 + k * 36).toDouble())
                    val x = 0.5f + r * kotlin.math.cos(a).toFloat()
                    val y = 0.52f + r * kotlin.math.sin(a).toFloat()
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }

            Icon.CLOCK -> {
                strokeStyle(0.07f)
                canvas.drawCircle(0.5f, 0.5f, 0.27f, paint)
                canvas.drawLine(0.5f, 0.5f, 0.5f, 0.33f, paint)
                canvas.drawLine(0.5f, 0.5f, 0.62f, 0.57f, paint)
            }

            Icon.CHART -> {
                fillStyle()
                rect.set(0.24f, 0.52f, 0.36f, 0.76f); canvas.drawRoundRect(rect, 0.02f, 0.02f, paint)
                rect.set(0.44f, 0.30f, 0.56f, 0.76f); canvas.drawRoundRect(rect, 0.02f, 0.02f, paint)
                rect.set(0.64f, 0.42f, 0.76f, 0.76f); canvas.drawRoundRect(rect, 0.02f, 0.02f, paint)
            }

            Icon.PALETTE -> {
                fillStyle()
                canvas.drawCircle(0.5f, 0.5f, 0.28f, paint)
                paint.color = fill
                canvas.drawCircle(0.60f, 0.64f, 0.07f, paint)
                paint.color = Color.rgb(255, 80, 80); canvas.drawCircle(0.38f, 0.40f, 0.05f, paint)
                paint.color = Color.rgb(255, 200, 0); canvas.drawCircle(0.52f, 0.33f, 0.05f, paint)
                paint.color = Color.rgb(60, 150, 255); canvas.drawCircle(0.66f, 0.42f, 0.05f, paint)
                paint.color = Color.rgb(60, 190, 90); canvas.drawCircle(0.37f, 0.57f, 0.05f, paint)
            }

            Icon.LOCK -> {
                strokeStyle(0.07f)
                rect.set(0.36f, 0.22f, 0.64f, 0.56f)
                canvas.drawArc(rect, 180f, 180f, false, paint)
                canvas.drawLine(0.36f, 0.39f, 0.36f, 0.47f, paint)
                canvas.drawLine(0.64f, 0.39f, 0.64f, 0.47f, paint)
                fillStyle()
                rect.set(0.27f, 0.45f, 0.73f, 0.78f)
                canvas.drawRoundRect(rect, 0.05f, 0.05f, paint)
                paint.color = fill
                canvas.drawCircle(0.5f, 0.59f, 0.045f, paint)
            }

            Icon.SHIELD -> {
                fillStyle()
                path.reset()
                path.moveTo(0.5f, 0.20f)
                path.lineTo(0.75f, 0.30f)
                path.cubicTo(0.75f, 0.55f, 0.66f, 0.70f, 0.5f, 0.80f)
                path.cubicTo(0.34f, 0.70f, 0.25f, 0.55f, 0.25f, 0.30f)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = fill
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.06f
                path.reset()
                path.moveTo(0.40f, 0.50f)
                path.lineTo(0.48f, 0.58f)
                path.lineTo(0.62f, 0.42f)
                canvas.drawPath(path, paint)
            }

            Icon.WIFI -> {
                strokeStyle(0.065f)
                for (k in 1..3) {
                    val r = 0.10f + k * 0.10f
                    rect.set(0.5f - r, 0.70f - r, 0.5f + r, 0.70f + r)
                    canvas.drawArc(rect, 225f, 90f, false, paint)
                }
                fillStyle()
                canvas.drawCircle(0.5f, 0.70f, 0.05f, paint)
            }

            Icon.DEVICE -> {
                strokeStyle(0.065f)
                rect.set(0.33f, 0.18f, 0.67f, 0.82f)
                canvas.drawRoundRect(rect, 0.06f, 0.06f, paint)
                fillStyle()
                canvas.drawCircle(0.5f, 0.73f, 0.035f, paint)
            }

            Icon.QR -> {
                // Three corner squares and a few dots, like a QR code
                strokeStyle(0.05f)
                for ((x, y) in listOf(0.24f to 0.24f, 0.56f to 0.24f, 0.24f to 0.56f)) {
                    rect.set(x, y, x + 0.20f, y + 0.20f)
                    canvas.drawRect(rect, paint)
                }
                fillStyle()
                for ((x, y) in listOf(0.31f to 0.31f, 0.63f to 0.31f, 0.31f to 0.63f)) {
                    rect.set(x, y, x + 0.06f, y + 0.06f)
                    canvas.drawRect(rect, paint)
                }
                for ((x, y) in listOf(0.58f to 0.58f, 0.70f to 0.58f, 0.64f to 0.66f, 0.58f to 0.72f, 0.72f to 0.72f)) {
                    rect.set(x, y, x + 0.07f, y + 0.07f)
                    canvas.drawRect(rect, paint)
                }
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
