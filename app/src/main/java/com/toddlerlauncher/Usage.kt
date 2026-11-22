package com.toddlerlauncher

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * How long each app has been used in toddler mode, per day (kept for 14 days),
 * and the screen-time rules worked out from it. Toddler Guard does the counting.
 */
class Usage(ctx: Context) {

    private val p = ctx.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val own = ctx.packageName

    private fun key(day: LocalDate, pkg: String) = "$day|$pkg"

    fun seconds(day: LocalDate, pkg: String): Long = p.getLong(key(day, pkg), 0L)

    fun add(pkg: String, secs: Long) {
        val today = LocalDate.now()
        val k = key(today, pkg)
        val e = p.edit().putLong(k, p.getLong(k, 0L) + secs)
        if (p.getString("pruned", "") != today.toString()) {
            val oldest = today.minusDays(14).toString()
            for (old in p.all.keys) {
                if (old.contains('|') && old.substringBefore('|') < oldest) e.remove(old)
            }
            e.putString("pruned", today.toString())
        }
        e.apply()
    }

    /** Every package with time on that day. */
    fun packages(day: LocalDate): Set<String> =
        p.all.keys.filter { it.startsWith("$day|") }.map { it.substringAfter('|') }.toSet()

    /** Time in apps (not counting the toddler home screen itself) on that day. */
    fun appSeconds(day: LocalDate): Long = packages(day).filter { it != own }.sumOf { seconds(day, it) }

    fun clearAll() {
        p.edit().clear().apply()
    }

    companion object {
        fun format(secs: Long): String {
            val m = (secs + 30) / 60
            return if (m < 60) "$m min" else String.format(Locale.UK, "%d h %02d min", m / 60, m % 60)
        }
    }
}

/** The screen-time rules: daily limit, per-app limits and allowed hours. */
object Rules {

    /** Why the toddler screen is locked right now, or null if it isn't. */
    enum class Block { LOCKED, HOURS, DAILY }

    fun dailyAllowanceSec(s: Settings): Long =
        if (s.dailyLimitMin <= 0) Long.MAX_VALUE else (s.dailyLimitMin + s.bonusToday()) * 60L

    fun dailyLeftSec(s: Settings, u: Usage): Long {
        val allowance = dailyAllowanceSec(s)
        return if (allowance == Long.MAX_VALUE) Long.MAX_VALUE else allowance - u.appSeconds(LocalDate.now())
    }

    fun appLeftSec(s: Settings, u: Usage, pkg: String): Long {
        val limit = s.appLimitMin(pkg)
        if (limit <= 0) return Long.MAX_VALUE
        return (limit + s.bonusToday()) * 60L - u.seconds(LocalDate.now(), pkg)
    }

    fun outsideHours(s: Settings): Boolean {
        if (!s.hoursOn) return false
        val weekend = LocalDate.now().dayOfWeek.let { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY }
        val start = if (weekend) s.weekendStart else s.weekdayStart
        val end = if (weekend) s.weekendEnd else s.weekdayEnd
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        return if (start <= end) now < start || now >= end else now in end until start // window past midnight
    }

    fun block(s: Settings, u: Usage): Block? = when {
        s.lockedNow -> Block.LOCKED
        outsideHours(s) -> Block.HOURS
        dailyLeftSec(s, u) <= 0 -> Block.DAILY
        else -> null
    }

    fun appBlocked(s: Settings, u: Usage, pkg: String) = appLeftSec(s, u, pkg) <= 0

    fun hhmm(minutes: Int) = String.format(Locale.UK, "%02d:%02d", minutes / 60, minutes % 60)
}
