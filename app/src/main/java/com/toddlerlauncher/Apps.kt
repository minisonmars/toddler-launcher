package com.toddlerlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.min

class AppEntry(val pkg: String, val label: String, val icon: Drawable)

object Apps {

    /** Every app with an icon in the app drawer, except this one, sorted by name. */
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(main, 0)
        val seen = HashSet<String>()
        val out = mutableListOf<AppEntry>()
        for (ri in found) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            out.add(AppEntry(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)))
        }
        return out.sortedBy { it.label.lowercase() }
    }

    /** The allowed apps that are still installed, in the parent's chosen order. */
    fun allowed(ctx: Context, settings: Settings): List<AppEntry> {
        val pm = ctx.packageManager
        return settings.allowed.mapNotNull { pkg ->
            try {
                if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                val info = pm.getApplicationInfo(pkg, 0)
                AppEntry(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/** One app on the toddler home screen: a big icon with an optional name underneath. */
@SuppressLint("ViewConstructor")
class AppTile(context: Context, val app: AppEntry, private val showName: Boolean, private val sizeFactor: Float) :
    View(context) {

    /** Grey and faded: the app's time is used up for today. */
    var greyed = false
        set(v) {
            field = v
            alpha = if (v) 0.55f else 1f
            invalidate()
        }
    private val greyFilter = android.graphics.ColorMatrixColorFilter(
        android.graphics.ColorMatrix().apply { setSaturation(0f) }
    )
    private val icon = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, Color.argb(200, 0, 0, 0))
    }

    init {
        isClickable = true
        isFocusable = true
        contentDescription = app.label
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        text.textSize = (min(w, h) * 0.13f).coerceIn(Ui.dp(context, 12).toFloat(), Ui.dp(context, 22).toFloat())
        val labelH = if (showName) text.textSize * 1.8f else 0f
        val side = (min(w * 0.92f, h - labelH) * sizeFactor).coerceAtLeast(1f)
        val top = (h - labelH - side) / 2f

        canvas.save()
        if (isPressed) canvas.scale(0.9f, 0.9f, w / 2f, top + side / 2f)
        val l = ((w - side) / 2f).toInt()
        val t = top.toInt()
        icon.colorFilter = if (greyed) greyFilter else null
        icon.setBounds(l, t, l + side.toInt(), t + side.toInt())
        icon.draw(canvas)
        canvas.restore()

        if (showName) {
            val name = TextUtils.ellipsize(app.label, text, w * 0.95f, TextUtils.TruncateAt.END).toString()
            text.color = if (greyed) Color.LTGRAY else Color.WHITE
            canvas.drawText(name, w / 2f, top + side + text.textSize * 1.3f, text)
        }
    }
}
