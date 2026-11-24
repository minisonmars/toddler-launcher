package com.toddlerlauncher.parent

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub for a newer Toddler Parent. */
object Updater {
    const val REPO = "minisonmars/toddler-launcher"

    class Latest(val version: String, val url: String)

    fun installed(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    /** Blocking. Null if there's nothing newer or GitHub can't be reached. */
    fun newer(ctx: Context): Latest? = try {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "ToddlerParent")
        val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        conn.disconnect()
        val v = o.getString("tag_name").removePrefix("v")
        val assets = o.getJSONArray("assets")
        var url = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").startsWith("ToddlerParent")) url = a.getString("browser_download_url")
        }
        if (url.isNotEmpty() && compare(v, installed(ctx)) > 0) Latest(v, url) else null
    } catch (e: Exception) {
        null
    }

    private fun compare(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
