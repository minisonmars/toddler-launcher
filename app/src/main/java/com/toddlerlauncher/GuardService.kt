package com.toddlerlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Build
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * Toddler Guard. Android tells it whenever a new window comes to the front.
 * If it belongs to an app that isn't allowed, it sends the child back to the
 * toddler home screen. If the notification shade or quick settings are pulled
 * down, it closes them again.
 *
 * It stands aside while a parent is in the parent area, and while protection is paused.
 */
class GuardService : AccessibilityService() {

    private lateinit var settings: Settings
    private var keyboards = setOf<String>()
    private var keyboardsCheckedAt = 0L
    private var lastActionAt = 0L

    private lateinit var usage: Usage
    private val handler = Handler(Looper.getMainLooper())
    private var foreground: String? = null
    private var lastTickAt = 0L
    private val warned = HashSet<String>()

    private fun isActive() =
        settings.guardOn && !settings.inParentMode() && !settings.isPaused() && !settings.isInstalling()

    override fun onServiceConnected() {
        settings = Settings(this)
        usage = Usage(this)
        showBlocker(isActive() && settings.blockPullDown)
        lastTickAt = SystemClock.elapsedRealtime()
        handler.postDelayed(tick, TICK_MS)
        // Volume changes: pull the media volume back down to the cap straight away
        contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI, true, volumeObserver
        )
    }

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            enforceVolume()
        }
    }

    /** Every few seconds: count time in the app on screen and apply the screen-time rules. */
    private val tick = object : Runnable {
        override fun run() {
            try {
                tickOnce()
            } catch (e: Exception) {
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun tickOnce() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = ((now - lastTickAt) / 1000).coerceIn(0, TICK_MS / 1000 + 5)
        lastTickAt = now
        val active = isActive()
        showBlocker(active && settings.blockPullDown)
        if (!active) return
        enforceVolume()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val fg = foreground ?: return
        if (!pm.isInteractive) return
        if (fg != packageName && !settings.isAllowed(fg)) return
        usage.add(fg, elapsed)
        if (fg == packageName) return

        if (Rules.block(settings, usage) != null || Rules.appBlocked(settings, usage, fg)) {
            goHome()
            return
        }
        if (settings.warnings) {
            val left = minOf(Rules.dailyLeftSec(settings, usage), Rules.appLeftSec(settings, usage, fg))
            val key = java.time.LocalDate.now().toString() + "|" + (if (left == Rules.dailyLeftSec(settings, usage)) "all" else fg) +
                "|" + (settings.dailyLimitMin + settings.bonusToday() + settings.appLimitMin(fg))
            if (left in 1L..300L && warned.add(key)) {
                Toast.makeText(this, "5 minutes left", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enforceVolume() {
        if (!::settings.isInitialized) return
        val cap = settings.volumeCap
        if (cap <= 0 || !isActive()) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val allowed = (max * cap + 50) / 100
            if (am.getStreamVolume(AudioManager.STREAM_MUSIC) > allowed) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, allowed, 0)
            }
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try {
            contentResolver.unregisterContentObserver(volumeObserver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::settings.isInitialized) settings = Settings(this)
        if (!::usage.isInitialized) usage = Usage(this)
        val active = isActive()
        showBlocker(active && settings.blockPullDown)
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.android.systemui" && !isKeyboard(pkg) && !isPermissionDialog(pkg)) foreground = pkg
        if (!active) return

        if (pkg == packageName || isKeyboard(pkg) || isPermissionDialog(pkg)) return
        if (settings.isAllowed(pkg)) {
            // Allowed, unless its time is used up or it's outside the allowed hours
            if (Rules.block(settings, usage) != null || Rules.appBlocked(settings, usage, pkg)) goHome()
            return
        }

        if (pkg == "com.android.systemui") {
            // Notification shade, quick settings, volume panel, lock screen... only the shade needs closing
            closeShade()
            return
        }

        // Anything else (another app, Settings, the phone's own home screen or recent apps): go home
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionAt < 300) return
        lastActionAt = now
        goHome()
    }

    private fun goHome() {
        if (Device.isDefaultHome(this)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            // Not the home app yet, so open the toddler screen directly
            try {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            } catch (e: Exception) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= 31) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            try {
                sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            } catch (e: Exception) {
            }
        }
    }

    /** On-screen keyboards must be allowed so the child's apps can still show them. */
    private fun isKeyboard(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - keyboardsCheckedAt > 60_000) {
            keyboardsCheckedAt = now
            keyboards = try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.enabledInputMethodList.map { it.packageName }.toSet()
            } catch (e: Exception) {
                emptySet()
            }
        }
        return keyboards.contains(pkg)
    }

    /** Android's own "Allow this app to use the camera?" style pop-ups, so allowed apps keep working. */
    private fun isPermissionDialog(pkg: String) = pkg.endsWith("permissioncontroller")

    // ---------- Invisible strip over the status bar, so a swipe down there does nothing ----------

    private var blocker: View? = null

    private fun showBlocker(show: Boolean) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val current = blocker
        if (show && current == null) {
            val v = View(this)
            v.setOnTouchListener { _, _ -> true } // swallow every touch
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            val barH = if (id > 0) resources.getDimensionPixelSize(id) else 0
            val height = maxOf(barH, (24 * resources.displayMetrics.density).toInt()) +
                (8 * resources.displayMetrics.density).toInt()
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSPARENT
            )
            lp.gravity = Gravity.TOP
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try {
                wm.addView(v, lp)
                blocker = v
            } catch (e: Exception) {
            }
        } else if (!show && current != null) {
            try {
                wm.removeView(current)
            } catch (e: Exception) {
            }
            blocker = null
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        showBlocker(false)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TICK_MS = 10_000L
    }
}
