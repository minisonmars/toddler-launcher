package com.toddlerlauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.abs

/** The toddler home screen: only the allowed apps, plus a hidden-in-plain-sight parent button. */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var root: FrameLayout
    private lateinit var wallImage: ImageView
    private lateinit var dim: View
    private lateinit var grid: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var dots: PageDots
    private lateinit var prev: RoundIconButton
    private lateinit var next: RoundIconButton
    private lateinit var gear: HoldButton
    private lateinit var update: RoundIconButton
    private lateinit var banner: TextView
    private lateinit var empty: TextView

    private var apps = listOf<AppEntry>()
    private var page = 0
    private var lastLaunchAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L
    private var wallShown = ""

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)

        root = FrameLayout(this)
        wallImage = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(wallImage, FrameLayout.LayoutParams(-1, -1))
        dim = View(this).apply { setBackgroundColor(Color.argb(70, 0, 0, 0)) }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))

        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(grid, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(80)
            bottomMargin = dp(88)
            leftMargin = dp(8)
            rightMargin = dp(8)
        })

        empty = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            setPadding(dp(32), 0, dp(32), 0)
        }
        root.addView(empty, FrameLayout.LayoutParams(-1, -1))

        // Page arrows and dots along the bottom
        prev = RoundIconButton(this, Icon.ARROW_LEFT, Ui.SHADE).apply {
            contentDescription = "Previous page"
            setOnClickListener { showPage(page - 1) }
        }
        next = RoundIconButton(this, Icon.ARROW_RIGHT, Ui.SHADE).apply {
            contentDescription = "Next page"
            setOnClickListener { showPage(page + 1) }
        }
        dots = PageDots(this)
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(prev, LinearLayout.LayoutParams(dp(68), dp(68)))
            addView(dots, LinearLayout.LayoutParams(dp(140), dp(24)))
            addView(next, LinearLayout.LayoutParams(dp(68), dp(68)))
        }
        root.addView(bottomBar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(12)
        })

        // Parent button: does nothing on a tap, opens the PIN or pattern after a long hold
        gear = HoldButton(this, Icon.GEAR, Ui.SHADE).apply {
            contentDescription = "Parent area (hold)"
            onHeld = { Gate.ask(this@MainActivity, settings) { openParent() } }
        }
        root.addView(gear, FrameLayout.LayoutParams(dp(60), dp(60)))

        // Update button on the home screen, only while the app is being built
        update = RoundIconButton(this, Icon.DOWNLOAD, Ui.SHADE).apply {
            contentDescription = "App updates"
            setOnClickListener { Updater.showDialog(this@MainActivity, settings) }
        }
        root.addView(update, FrameLayout.LayoutParams(dp(60), dp(60)))

        banner = TextView(this).apply {
            text = "Protection paused. Hold the gear to switch it back on."
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Ui.ORANGE)
                cornerRadius = dp(16).toFloat()
            }
        }
        root.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(24)
            leftMargin = dp(80)
            rightMargin = dp(80)
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Back on the toddler screen, so the parent area is over and the guard is on duty again
        // (unless an update is installing: Android's install and Play Protect screens come and go)
        if (!settings.isInstalling()) settings.endParentMode()

        // Using another home screen for a while: Home goes straight on to it. Opening
        // Toddler Launcher from its app icon instead ends that and brings the toddler screen back.
        if (settings.usingOtherHome()) {
            if (intent?.hasCategory(Intent.CATEGORY_HOME) == true) {
                if (openOtherHome()) return
            } else {
                settings.stopOtherHome()
                Ui.toast(this, "Back to the toddler screen. Protection is on again.")
            }
        } else if (settings.otherHome.isNotEmpty()) {
            settings.stopOtherHome() // the time ran out
        }

        // Just chose this as the home app from setup or the parent area: go back there
        val back = settings.returnTo
        if (back.isNotEmpty()) {
            settings.returnTo = ""
            if (System.currentTimeMillis() < settings.returnUntil && Device.isDefaultHome(this)) {
                try {
                    settings.startParentMode()
                    startActivity(Intent(this, Class.forName(back)))
                    return
                } catch (e: Exception) {
                }
            }
        }

        if (!settings.setupShown) {
            settings.setupShown = true
            settings.startParentMode()
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }

        applyWindowOptions()
        applyWallpaper()
        placeCornerButtons()
        banner.visibility = if (settings.isPaused()) View.VISIBLE else View.GONE

        apps = Apps.allowed(this, settings)
        showPage(page)

        if (settings.pinScreen && !Device.isScreenPinned(this)) {
            try {
                startLockTask()
            } catch (e: Exception) {
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun openOtherHome(): Boolean {
        val cn = ComponentName.unflattenFromString(settings.otherHome) ?: return false
        return try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            settings.stopOtherHome()
            false
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyWindowOptions()
    }

    @Deprecated("Framework callback")
    override fun onBackPressed() {
        // Back does nothing on the toddler screen
    }

    private fun openParent() {
        Device.unpin(this)
        settings.startParentMode()
        startActivity(Intent(this, ParentActivity::class.java))
    }

    // ---------- Screen options ----------

    @Suppress("DEPRECATION")
    private fun applyWindowOptions() {
        requestedOrientation = when (settings.orientation) {
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (settings.hideBars) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.decorView.systemUiVisibility = 0
        }
    }

    private fun applyWallpaper() {
        val photo = File(filesDir, WALLPAPER_FILE)
        val key = settings.wallpaper + "|" + settings.dimPhoto + "|" + photo.lastModified()
        if (key == wallShown) return
        wallShown = key
        if (settings.wallpaper == "photo" && photo.exists()) {
            val dm = resources.displayMetrics
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photo.path, bounds)
            var sample = 1
            val target = maxOf(dm.widthPixels, dm.heightPixels)
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
            val bmp = BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample })
            wallImage.setImageBitmap(bmp)
            root.background = null
            root.setBackgroundColor(Color.BLACK)
            dim.visibility = if (settings.dimPhoto) View.VISIBLE else View.GONE
        } else {
            wallImage.setImageDrawable(null)
            val w = Settings.WALLPAPERS.firstOrNull { it.first == settings.wallpaper }?.second
                ?: Settings.WALLPAPERS.first().second
            root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(w.second, w.third))
            dim.visibility = View.GONE
        }
    }

    private fun placeCornerButtons() {
        val corner = settings.gearCorner
        val top = corner.startsWith("t")
        val right = corner.endsWith("r")
        val vertical = if (top) Gravity.TOP else Gravity.BOTTOM
        (gear.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = vertical or (if (right) Gravity.END else Gravity.START)
            setMargins(dp(12), dp(12), dp(12), dp(12))
        }
        // The update button sits in the other corner along the same edge
        (update.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = vertical or (if (right) Gravity.START else Gravity.END)
            setMargins(dp(12), dp(12), dp(12), dp(12))
        }
        gear.holdMs = settings.holdSeconds * 1000L
        update.visibility = if (settings.showUpdateOnHome) View.VISIBLE else View.GONE
        gear.requestLayout()
        update.requestLayout()
    }

    // ---------- The grid of apps ----------

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** The grid as set by the parent, swapped round when the screen is sideways (if that's switched on). */
    private fun gridCols() = (if (isLandscape() && settings.swapWhenSideways) settings.rows else settings.columns).coerceAtLeast(1)
    private fun gridRows() = (if (isLandscape() && settings.swapWhenSideways) settings.columns else settings.rows).coerceAtLeast(1)

    /** Sideways there is little height, so the corner buttons sit beside the grid instead of above and below it. */
    private fun placeGrid() {
        (grid.layoutParams as FrameLayout.LayoutParams).apply {
            if (isLandscape()) {
                topMargin = dp(12)
                bottomMargin = if (pageCount() > 1) dp(84) else dp(12)
                leftMargin = dp(80)
                rightMargin = dp(80)
            } else {
                topMargin = dp(80)
                bottomMargin = dp(88)
                leftMargin = dp(8)
                rightMargin = dp(8)
            }
        }
        grid.requestLayout()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        showPage(page)
    }

    private fun perPage() = gridRows() * gridCols()
    private fun pageCount() = ((apps.size + perPage() - 1) / perPage()).coerceAtLeast(1)

    private fun showPage(p: Int) {
        page = p.coerceIn(0, pageCount() - 1)
        grid.removeAllViews()

        if (apps.isEmpty()) {
            empty.visibility = View.VISIBLE
            empty.text = "No apps chosen yet.\n\nHold the gear button for ${settings.holdSeconds} seconds, " +
                "enter the parent code, then choose the apps your child can use."
        } else {
            empty.visibility = View.GONE
        }

        val sizeFactor = when (settings.iconSize) {
            "small" -> 0.55f
            "medium" -> 0.72f
            else -> 0.9f
        }
        val cols = gridCols()
        val rows = gridRows()
        val start = page * perPage()
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (c in 0 until cols) {
                val i = start + r * cols + c
                val cell: View = if (i < apps.size) {
                    AppTile(this, apps[i], settings.showNames, sizeFactor).apply {
                        setOnClickListener { open(app) }
                    }
                } else {
                    View(this)
                }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        placeGrid()
        val many = pageCount() > 1
        bottomBar.visibility = if (many) View.VISIBLE else View.INVISIBLE
        prev.visibility = if (page > 0) View.VISIBLE else View.INVISIBLE
        next.visibility = if (page < pageCount() - 1) View.VISIBLE else View.INVISIBLE
        dots.count = pageCount()
        dots.current = page
    }

    private fun open(app: AppEntry) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchAt < 1500) return // little fingers often tap twice
        lastLaunchAt = now
        Device.unpin(this) // screen pinning would stop the app opening
        if (!Apps.launch(this, app.pkg)) Ui.toast(this, "${app.label} couldn't be opened")
    }

    /** A quick sideways swipe also changes page. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downAt = SystemClock.uptimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > dp(90) && abs(dx) > abs(dy) * 1.5f && SystemClock.uptimeMillis() - downAt < 700) {
                    showPage(if (dx < 0) page + 1 else page - 1)
                    ev.action = MotionEvent.ACTION_CANCEL
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val WALLPAPER_FILE = "wallpaper.jpg"
    }
}
