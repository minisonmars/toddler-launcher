package com.toddlerlauncher

import android.app.Activity
import android.app.AlertDialog
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Asks for the parent PIN or pattern, and lets parents set new ones. */
object Gate {

    private var wrongCount = 0
    private var lockedUntil = 0L

    fun ask(activity: Activity, settings: Settings, onOk: () -> Unit) {
        val wait = (lockedUntil - SystemClock.elapsedRealtime()) / 1000
        if (wait > 0) {
            Ui.toast(activity, "Too many wrong tries. Wait $wait seconds.")
            return
        }
        if (settings.lockType == "pattern") askPattern(activity, settings, onOk) else askPin(activity, settings, onOk)
    }

    private fun wrong(activity: Activity) {
        wrongCount++
        if (wrongCount >= 5) {
            wrongCount = 0
            lockedUntil = SystemClock.elapsedRealtime() + 30_000
            Ui.toast(activity, "Too many wrong tries. Wait 30 seconds.")
        } else {
            Ui.toast(activity, "That's not right")
        }
    }

    private fun right(onOk: () -> Unit) {
        wrongCount = 0
        onOk()
    }

    private fun askPin(activity: Activity, settings: Settings, onOk: () -> Unit) {
        // Keys sized to fit the screen, sideways too
        val dm = activity.resources.displayMetrics
        val room = (minOf(dm.heightPixels, dm.widthPixels) - Ui.dp(activity, 250)) / 4
        val keySize = room.coerceIn(Ui.dp(activity, 44), Ui.dp(activity, 68))
        val pad = PinPad(activity, keySize)
        lateinit var dialog: AlertDialog
        // Opens as soon as the right PIN is in (no need to press the tick)
        pad.onChange = { digits ->
            if (digits.length >= 4 && settings.checkPin(digits)) {
                dialog.dismiss()
                right(onOk)
            }
        }
        pad.onEnter = { digits ->
            if (digits.isEmpty()) {
                // nothing typed yet
            } else if (settings.checkPin(digits)) {
                dialog.dismiss()
                right(onOk)
            } else {
                wrong(activity)
                if (SystemClock.elapsedRealtime() < lockedUntil) dialog.dismiss() else pad.showWrong()
            }
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Parent PIN")
            .setView(android.widget.ScrollView(activity).apply { addView(boxed(activity, pad)) })
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Forgot?") { _, _ -> forgot(activity, settings, onOk) }
            .create()
        dialog.show()
    }

    private fun askPattern(activity: Activity, settings: Settings, onOk: () -> Unit) {
        val pattern = PatternView(activity)
        lateinit var dialog: AlertDialog
        pattern.onDone = { dots ->
            if (settings.checkPattern(dots)) {
                dialog.dismiss()
                right(onOk)
            } else {
                pattern.errorShown = true
                wrong(activity)
                pattern.postDelayed({ pattern.clear() }, 700)
                if (SystemClock.elapsedRealtime() < lockedUntil) dialog.dismiss()
            }
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Draw the parent pattern")
            .setView(boxed(activity, pattern))
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Use PIN") { _, _ -> askPin(activity, settings, onOk) }
            .create()
        dialog.show()
    }

    private fun forgot(activity: Activity, settings: Settings, onOk: () -> Unit) {
        if (!settings.hasRecoveryWord()) {
            AlertDialog.Builder(activity)
                .setTitle("No recovery word set")
                .setMessage(
                    "Without a recovery word the only way back in is to clear this app's storage. " +
                        "Restart the device in safe mode (this switches Toddler Guard off), then go to " +
                        "Settings > Apps > Toddler Launcher > Storage > Clear data. All settings are reset."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val word = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Recovery word"
        }
        AlertDialog.Builder(activity)
            .setTitle("Reset PIN")
            .setMessage("Enter your recovery word. The PIN goes back to ${Settings.DEFAULT_PIN} and any pattern is removed.")
            .setView(boxed(activity, word))
            .setPositiveButton("Reset") { _, _ ->
                if (settings.checkRecoveryWord(word.text.toString())) {
                    settings.resetUnlock()
                    Ui.toast(activity, "PIN reset to ${Settings.DEFAULT_PIN}. Please set a new one.")
                    right(onOk)
                } else {
                    wrong(activity)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- Setting a new PIN, pattern or recovery word ----------

    fun changePin(activity: Activity, settings: Settings, onDone: () -> Unit) {
        val newPin = pinField(activity, "New PIN (4 to 8 digits)")
        val again = pinField(activity, "New PIN again")
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(newPin)
            addView(again)
        }
        lateinit var dialog: AlertDialog
        // The keyboard's Enter key on the second box saves, like the Save button
        again.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        again.setOnEditorActionListener { _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
            true
        }
        newPin.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        dialog = AlertDialog.Builder(activity)
            .setTitle("Change PIN")
            .setView(boxed(activity, box))
            .setPositiveButton("Save") { _, _ ->
                val pin = newPin.text.toString()
                when {
                    pin.length !in 4..8 -> Ui.toast(activity, "The PIN needs 4 to 8 digits")
                    pin != again.text.toString() -> Ui.toast(activity, "The two PINs don't match")
                    else -> {
                        settings.setPin(pin)
                        Ui.toast(activity, "PIN changed")
                        onDone()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    fun changePattern(activity: Activity, settings: Settings, onDone: () -> Unit) {
        val info = TextView(activity).apply {
            text = "Join at least 4 dots"
            textSize = 16f
        }
        val pattern = PatternView(activity)
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(info)
            addView(pattern, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        lateinit var dialog: AlertDialog
        var first: List<Int>? = null
        pattern.onDone = { dots ->
            val f = first
            when {
                dots.size < 4 -> {
                    info.text = "Too short. Join at least 4 dots"
                    pattern.errorShown = true
                    pattern.postDelayed({ pattern.clear() }, 700)
                }
                f == null -> {
                    first = dots
                    info.text = "Now draw it again to check"
                    pattern.postDelayed({ pattern.clear() }, 400)
                }
                f == dots -> {
                    settings.setPattern(dots)
                    settings.lockType = "pattern"
                    dialog.dismiss()
                    Ui.toast(activity, "Pattern saved. It's now used to open the parent area.")
                    onDone()
                }
                else -> {
                    first = null
                    info.text = "They didn't match. Start again"
                    pattern.errorShown = true
                    pattern.postDelayed({ pattern.clear() }, 700)
                }
            }
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle("New pattern")
            .setView(boxed(activity, box))
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    fun changeRecoveryWord(activity: Activity, settings: Settings, onDone: () -> Unit) {
        val word = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Recovery word"
        }
        AlertDialog.Builder(activity)
            .setTitle("Recovery word")
            .setMessage("If you forget the PIN or pattern, this word resets it. Capitals don't matter.")
            .setView(boxed(activity, word))
            .setPositiveButton("Save") { _, _ ->
                if (word.text.toString().isBlank()) {
                    Ui.toast(activity, "Please enter a word")
                } else {
                    settings.setRecoveryWord(word.text.toString())
                    Ui.toast(activity, "Recovery word saved")
                    onDone()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pinField(activity: Activity, hintText: String) = EditText(activity).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        hint = hintText
        textSize = 22f
    }

    private fun boxed(activity: Activity, view: View): FrameLayout =
        FrameLayout(activity).apply {
            val pad = Ui.dp(activity, 24)
            setPadding(pad, Ui.dp(activity, 8), pad, 0)
            addView(view)
        }
}
