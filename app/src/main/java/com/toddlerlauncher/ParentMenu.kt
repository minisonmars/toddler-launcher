package com.toddlerlauncher

import android.content.Context
import java.time.LocalDate

/** The line under each parent area section (also sent to the parent app and the web page). */
object ParentMenu {
    class Line(val subtitle: String, val warn: Boolean = false)

    fun line(ctx: Context, key: String): Line {
        val s = Settings(ctx)
        return when (key) {
            "checks" -> {
                val missing = Checklist.missing(ctx, s)
                if (missing.isEmpty()) Line("Everything is set up") else Line("Needs attention: " + missing.joinToString(", "), true)
            }
            "apps" -> Line("${s.allowed.size} apps allowed")
            "recommended" -> Line("Install other toddler apps made for this launcher")
            "time" -> {
                val u = Usage(ctx)
                val limit = if (s.dailyLimitMin > 0) " of " + Usage.format(Rules.dailyAllowanceSec(s)) else ""
                Line("Today: " + Usage.format(u.countedSeconds(LocalDate.now(), s)) + limit +
                    (if (s.hoursOn) ". Allowed hours on" else "") + (if (s.lockedNow) ". Break time now" else ""), s.lockedNow)
            }
            "report" -> Line("The last 3 days and a 7-day average")
            "look" -> Line("Name, grid, icons, wallpaper, screen direction")
            "code" -> if (s.isDefaultPin() && !s.hasPattern()) Line("Still using the default PIN ${Settings.DEFAULT_PIN}", true)
                else Line("PIN, pattern, recovery word, gear button")
            "protect" -> if (s.isPaused()) Line("Protection is PAUSED", true)
                else Line("Toddler Guard, pull-down block, volume cap, pinning")
            "connect" -> Line("Quick switches for you")
            "remote" -> Line(if (s.remoteOn) "On: Toddler Parent app or your phone's browser" else "Off")
            "updates" -> {
                val v = Updater.available(ctx)
                if (v != null) Line("Version $v is ready to install (you have ${Updater.installedVersion(ctx)})", true)
                else Line("You have version ${Updater.installedVersion(ctx)}")
            }
            "device" -> Line("Use your normal home screen, uninstall")
            else -> Line("")
        }
    }
}
