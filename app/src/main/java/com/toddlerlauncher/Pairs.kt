package com.toddlerlauncher

import android.content.Context
import com.toddlerlauncher.link.Link
import org.json.JSONObject

/** Parent phones paired with this device (Toddler Parent app), each with its own secret key. */
object Pairs {

    class Phone(val id: String, val name: String, val pairedAt: Long, val lastSeen: Long)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("pairs", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Phone> = prefs(ctx).all.mapNotNull { (id, v) ->
        try {
            val o = JSONObject(v as String)
            Phone(id, o.optString("name"), o.optLong("at"), o.optLong("seen"))
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.pairedAt }

    fun key(ctx: Context, id: String): ByteArray? {
        if (id.isEmpty()) return null
        val v = prefs(ctx).getString(id, null) ?: return null
        return try {
            Link.unb64(JSONObject(v).getString("key"))
        } catch (e: Exception) {
            null
        }
    }

    fun add(ctx: Context, id: String, key: ByteArray, name: String) {
        val now = System.currentTimeMillis()
        prefs(ctx).edit().putString(id, JSONObject().put("key", Link.b64(key)).put("name", name)
            .put("at", now).put("seen", now).toString()).apply()
    }

    /** Notes when a phone was last connected (at most once a minute, to save writes). */
    fun touch(ctx: Context, id: String) {
        val v = prefs(ctx).getString(id, null) ?: return
        val o = JSONObject(v)
        val now = System.currentTimeMillis()
        if (now - o.optLong("seen") < 60_000L) return
        prefs(ctx).edit().putString(id, o.put("seen", now).toString()).apply()
    }

    fun remove(ctx: Context, id: String) = prefs(ctx).edit().remove(id).apply()
}
