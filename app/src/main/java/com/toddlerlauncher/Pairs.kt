package com.toddlerlauncher

import android.content.Context
import com.toddlerlauncher.link.Link
import org.json.JSONObject

/** Parent phones paired with this device (Toddler Parent app), each with its own secret key. */
object Pairs {

    class Phone(val id: String, val name: String, val pairedAt: Long, val lastSeen: Long)

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("pairs", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Phone> {
        tidyOnce(ctx)
        return read(ctx)
    }

    private fun read(ctx: Context): List<Phone> = prefs(ctx).all.mapNotNull { (id, v) ->
        if (id == TIDIED) return@mapNotNull null
        try {
            val o = JSONObject(v as String)
            Phone(id, o.optString("name"), o.optLong("at"), o.optLong("seen"))
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.pairedAt }

    private const val TIDIED = "_tidied"

    /**
     * In older versions, pairing a phone again (for example from another Wi-Fi) added a second copy.
     * Only the newest copy of each phone still works, so the older ones are removed, once.
     */
    private fun tidyOnce(ctx: Context) {
        val p = prefs(ctx)
        if (p.contains(TIDIED)) return
        val e = p.edit()
        for ((_, copies) in read(ctx).groupBy { it.name }) {
            copies.sortedByDescending { maxOf(it.lastSeen, it.pairedAt) }.drop(1).forEach { e.remove(it.id) }
        }
        e.putString(TIDIED, "{}").apply()
    }

    fun key(ctx: Context, id: String): ByteArray? {
        if (id.isEmpty()) return null
        val v = prefs(ctx).getString(id, null) ?: return null
        return try {
            Link.unb64(JSONObject(v).getString("key"))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves a new pairing. An older pairing from the same phone (same phone id, or the same
     * name from before phones sent an id) is dead once the phone re-pairs, so it's removed:
     * that stops the list filling up with copies of one phone.
     */
    fun add(ctx: Context, id: String, key: ByteArray, name: String, phoneId: String = "") {
        val now = System.currentTimeMillis()
        val e = prefs(ctx).edit()
        for ((old, v) in prefs(ctx).all) {
            if (old == TIDIED) continue
            try {
                val o = JSONObject(v as String)
                val oldPhone = o.optString("phone")
                val same = if (phoneId.isNotEmpty() && oldPhone.isNotEmpty()) oldPhone == phoneId
                else o.optString("name") == name
                if (same) e.remove(old)
            } catch (ex: Exception) {
            }
        }
        e.putString(id, JSONObject().put("key", Link.b64(key)).put("name", name).put("phone", phoneId)
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
