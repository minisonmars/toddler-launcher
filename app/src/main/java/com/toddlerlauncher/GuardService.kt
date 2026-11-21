package com.toddlerlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

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

    override fun onServiceConnected() {
        settings = Settings(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!::settings.isInitialized) settings = Settings(this)
        if (!settings.guardOn || settings.inParentMode() || settings.isPaused()) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName || settings.isAllowed(pkg) || isKeyboard(pkg) || isPermissionDialog(pkg)) return

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

    override fun onInterrupt() {}
}
