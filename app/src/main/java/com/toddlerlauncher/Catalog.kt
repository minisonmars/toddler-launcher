package com.toddlerlauncher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The recommended apps (Ben's other toddler apps). The list is read from
 * catalog.json in this app's GitHub repo, so new apps appear without updating
 * the launcher. The built-in copy is used if GitHub can't be reached.
 */
object Catalog {

    class Entry(val name: String, val pkg: String, val repo: String, val description: String)

    private const val URL_JSON = "https://raw.githubusercontent.com/minisonmars/toddler-launcher/main/catalog.json"

    private val BUILT_IN = listOf(
        Entry(
            "Toddler Camera", "com.toddlercamera", "minisonmars/toddler-camera",
            "A big-button camera and gallery, with its own PIN-protected parent settings."
        )
    )

    /** Blocking, so call it off the main thread. */
    fun fetch(): List<Entry> =
        try {
            val conn = URL(URL_JSON).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("User-Agent", "ToddlerLauncher")
                if (conn.responseCode != 200) BUILT_IN
                else {
                    val apps = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("apps")
                    val out = (0 until apps.length()).map { i ->
                        val a = apps.getJSONObject(i)
                        Entry(a.getString("name"), a.getString("package"), a.getString("repo"), a.optString("description"))
                    }
                    out.ifEmpty { BUILT_IN }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            BUILT_IN
        }

    /** The installed version, or null if it isn't installed. */
    fun installedVersion(ctx: Context, pkg: String): String? =
        try {
            ctx.packageManager.getPackageInfo(pkg, 0).versionName ?: "?"
        } catch (e: Exception) {
            null
        }
}
