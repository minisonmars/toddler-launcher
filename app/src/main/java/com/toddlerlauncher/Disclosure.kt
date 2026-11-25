package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog

/**
 * Explains why Toddler Guard needs Android's accessibility service before the parent is
 * sent to accessibility settings. Google Play asks for this ("prominent disclosure"):
 * what it uses, why, that nothing leaves the device, and a clear Agree.
 */
object Disclosure {

    const val ACCESSIBILITY_TEXT =
        "Toddler Launcher uses Android's accessibility service (AccessibilityService API) for its " +
            "Toddler Guard parental control feature.\n\n" +
            "What it uses: only the name of the app or screen that opens (for example Settings, or the " +
            "notification shade).\n\n" +
            "Why: so it can send your child straight back to the toddler home screen when they open an app " +
            "you haven't allowed, pull down the notification shade, or their screen time is up. To do this it can press Home, " +
            "close the notification shade, and show small messages from you over other apps.\n\n" +
            "It does not read what is on the screen or anything typed, and it doesn't collect, store or share " +
            "any of this. Nothing leaves the device.\n\n" +
            "You can switch Toddler Guard off at any time in Android's accessibility settings."

    /** Shows the explanation; [onAgree] runs only if the parent taps Agree. */
    fun accessibility(a: Activity, onAgree: () -> Unit) {
        AlertDialog.Builder(a)
            .setTitle("Toddler Guard uses accessibility")
            .setMessage(ACCESSIBILITY_TEXT)
            .setPositiveButton("Agree") { _, _ -> onAgree() }
            .setNegativeButton("No thanks", null)
            .show()
    }
}
