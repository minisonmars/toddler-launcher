package com.toddlerlauncher

import android.content.Context
import android.media.AudioManager

/** The media volume (music, videos, games), read and set as a percentage. */
object Volume {

    private fun am(ctx: Context) = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** The volume now, 0 to 100. */
    fun percent(ctx: Context): Int = try {
        val am = am(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 + max / 2) / max
    } catch (e: Exception) {
        0
    }

    /** The highest the volume can go: the cap if one is set, otherwise 100. */
    fun highest(settings: Settings): Int = if (settings.volumeCap > 0) settings.volumeCap else 100

    /** Sets the volume (kept under the cap). Returns the volume it ended up at. */
    fun set(ctx: Context, settings: Settings, percent: Int): Int {
        return try {
            val am = am(ctx)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = percent.coerceIn(0, highest(settings))
            var step = (max * pct + 50) / 100
            // Never round up past the cap
            if (settings.volumeCap > 0) step = minOf(step, (max * settings.volumeCap + 50) / 100)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
            percent(ctx)
        } catch (e: Exception) {
            percent(ctx)
        }
    }

    /** Volume choices for the parent area and the remote apps, up to the cap. */
    fun choices(settings: Settings): List<Pair<String, String>> =
        (0..10).map { it * 10 }.filter { it <= highest(settings) }
            .map { it.toString() to if (it == 0) "Muted (0%)" else "$it%" }

    /** The nearest choice to the volume now, so the list shows the right one. */
    fun nearestChoice(ctx: Context, settings: Settings): String {
        val p = percent(ctx)
        return choices(settings).minByOrNull { kotlin.math.abs(it.first.toInt() - p) }?.first ?: "0"
    }
}
