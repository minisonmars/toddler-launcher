package com.toddlerlauncher

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * From target API 35, Android 15 and later draw apps edge to edge, under the status bar,
 * navigation bar, camera cut-out and keyboard. This pads a screen's root view (on top of
 * its own padding) so nothing hides behind them. When the bars are hidden (full screen /
 * toddler mode) their insets are 0, so nothing moves. Older Android versions don't draw
 * edge to edge and pass 0 here too.
 */
fun <T : View> T.fitSystemBars(): T {
    val l = paddingLeft
    val t = paddingTop
    val r = paddingRight
    val b = paddingBottom
    setOnApplyWindowInsetsListener { v, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
            )
            v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom)
        }
        insets
    }
    requestApplyInsets()
    return this
}
