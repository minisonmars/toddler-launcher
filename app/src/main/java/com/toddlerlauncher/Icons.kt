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

/**
 * The child's name in big colourful letters. Tapping it makes the letters
 * bounce, wave, wiggle, spin, grow or wobble.
 */
class ChildNameView(context: Context) : View(context) {

    var name: String = ""
        set(v) {
            field = v
            invalidate()
        }

    /** Extra space between letters, as a fraction of the letter size. */
    var spacing: Float = 0f
        set(v) {
            field = v
            invalidate()
        }

    var colours: IntArray = Settings.nameColourSet("rainbow")
        set(v) {
            field = v
            invalidate()
        }

    /** A key from Settings.NAME_ACTIONS. */
    var action: String = "random"

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("casual", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = fill.typeface
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        setShadowLayer(8f, 0f, 3f, Color.argb(140, 0, 0, 0))
    }

    private val moves = listOf("bounce", "wave", "wiggle", "spin", "grow", "jelly")
    private var playing = ""
    private var startedAt = 0L
    private var lastRandom = ""

    private val stagger = 70L
    private fun duration(): Long = when (playing) {
        "spin" -> 900L
        "wiggle", "jelly" -> 1000L
        else -> 1100L
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (name.isBlank() || action == "off") return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            playing = if (action == "random") moves.filter { it != lastRandom }.random() else action
            lastRandom = playing
            startedAt = SystemClock.uptimeMillis()
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            postInvalidateOnAnimation()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val text = name.trim()
        if (text.isEmpty()) return
        // Leave room above and below so jumping and growing letters aren't cut off
        var size = height * 0.6f
        fill.textSize = size
        fun totalWidth(): Float {
            val gap = fill.textSize * spacing
            return text.sumOf { fill.measureText(it.toString()).toDouble() }.toFloat() + gap * (text.length - 1)
        }
        val maxW = width * 0.94f
        val w = totalWidth()
        if (w > maxW) {
            size *= maxW / w
            fill.textSize = size
        }
        outline.textSize = size
        outline.strokeWidth = size * 0.14f
        outline.color = if (colours.size == 1 && colours[0] == Color.WHITE) Color.rgb(60, 60, 90) else Color.WHITE
        val gap = size * spacing
        var x = (width - totalWidth()) / 2f
        val baseY = height / 2f - (fill.descent() + fill.ascent()) / 2f
        val midY = height / 2f

        val now = SystemClock.uptimeMillis()
        val elapsed = now - startedAt
        val dur = duration()
        val running = playing.isNotEmpty() && elapsed < dur + stagger * text.length

        var colourIndex = 0
        for ((i, ch) in text.withIndex()) {
            val s = ch.toString()
            val cw = fill.measureText(s)
            val cx = x + cw / 2f
            // Little up-and-down resting pattern
            var dy = if (i % 2 == 0) -size * 0.04f else size * 0.04f
            var rot = 0f
            var sx = 1f
            var sy = 1f
            if (running) {
                val delay = if (playing == "wave" || playing == "spin") stagger * i else 0L
                val t = ((elapsed - delay).toFloat() / dur).coerceIn(0f, 1f)
                val pi = Math.PI.toFloat()
                when (playing) {
                    "bounce" -> { // three hops, each lower than the last
                        val hop = kotlin.math.abs(kotlin.math.sin(t * 3 * pi))
                        dy -= size * 0.3f * hop * (1f - t)
                    }
                    "wave" -> dy -= size * 0.3f * kotlin.math.sin(t * pi)
                    "wiggle" -> rot = 20f * kotlin.math.sin(t * 6 * pi) * (1f - t) * (if (i % 2 == 0) 1f else -1f)
                    "spin" -> rot = 360f * (t * t * (3 - 2 * t))
                    "grow" -> { val g = 1f + 0.4f * kotlin.math.sin(t * pi); sx = g; sy = g }
                    "jelly" -> {
                        val k = 0.3f * kotlin.math.sin(t * 5 * pi) * (1f - t)
                        sx = 1f + k
                        sy = 1f - k
                    }
                }
            }
            canvas.save()
            canvas.translate(0f, dy)
            if (rot != 0f) canvas.rotate(rot, cx, midY)
            if (sx != 1f || sy != 1f) canvas.scale(sx, sy, cx, if (playing == "jelly") baseY else midY)
            canvas.drawText(s, cx, baseY, outline)
            if (!ch.isWhitespace()) {
                fill.color = colours[colourIndex % colours.size]
                colourIndex++
            }
            canvas.drawText(s, cx, baseY, fill)
            canvas.restore()
            x += cw + gap
        }
        if (running) postInvalidateOnAnimation() else playing = ""
    }
}
