package com.toddlerlauncher.parent

import android.content.Context
import com.toddlerlauncher.link.Link
import org.json.JSONObject

/**
 * One way of reaching a child device: its address on a Wi-Fi network (or hotspot).
 * A device can have several, for example home Wi-Fi and a grandparent's Wi-Fi.
 */
class Route(var host: String, var port: Int, var label: String, var lastOk: Long = 0L) {
    fun toJson(): JSONObject = JSONObject().put("host", host).put("port", port).put("label", label).put("ok", lastOk)

    companion object {
        fun fromJson(o: JSONObject) = Route(o.optString("host"), o.optInt("port", Link.PORT), o.optString("label"), o.optLong("ok"))

        /** A starting name, from the first three parts of the address (each Wi-Fi usually has its own). */
        fun defaultLabel(host: String): String {
            val parts = host.split('.')
            return if (parts.size == 4) "Wi-Fi network ${parts[0]}.${parts[1]}.${parts[2]}.x" else "Address $host"
        }
    }
}

/** A paired child device. */
class Child(
    val id: String,          // the launcher's device id
    var name: String,
    val pair: String,        // our pairing id on that device
    val key: ByteArray,      // the secret key both sides share
    var host: String,        // last address that worked
    var port: Int,
    var ssid: String = "",   // its private hotspot, if paired that way
    var pass: String = "",
    var sec: String = "",
    val routes: MutableList<Route> = mutableListOf(),
) {
    /** Adds an address (or notes that it worked). Returns true if it's new. */
    fun remember(host: String, port: Int, worked: Boolean, label: String = ""): Boolean {
        if (host.isEmpty() || Direct.isOnHotspot(host)) return false
        val r = routes.firstOrNull { it.host == host && it.port == port }
        if (r != null) {
            if (worked) r.lastOk = System.currentTimeMillis()
            if (label.isNotEmpty()) r.label = label
            return false
        }
        routes.add(Route(host, port, label.ifEmpty { Route.defaultLabel(host) }, if (worked) System.currentTimeMillis() else 0L))
        return true
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("pair", pair).put("key", Link.b64(key))
        .put("host", host).put("port", port).put("ssid", ssid).put("pass", pass).put("sec", sec)
        .put("routes", org.json.JSONArray().apply { synchronized(routes) { routes.toList() }.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): Child {
            val c = Child(
                o.getString("id"), o.optString("name"), o.getString("pair"), Link.unb64(o.getString("key")),
                o.optString("host"), o.optInt("port", Link.PORT), o.optString("ssid"), o.optString("pass"), o.optString("sec")
            )
            val arr = o.optJSONArray("routes")
            if (arr != null) for (i in 0 until arr.length()) c.routes.add(Route.fromJson(arr.getJSONObject(i)))
            // Saved before a device could have several addresses: start the list with the last one that worked
            if (c.routes.isEmpty() && c.host.isNotEmpty()) c.routes.add(Route(c.host, c.port, Route.defaultLabel(c.host)))
            return c
        }
    }
}

/** Paired child devices, kept on this phone. */
object Store {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("children", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Child> = prefs(ctx).all.values.mapNotNull {
        try {
            Child.fromJson(JSONObject(it as String))
        } catch (e: Exception) {
            null
        }
    }.sortedBy { it.name.lowercase() }

    fun get(ctx: Context, id: String): Child? = list(ctx).firstOrNull { it.id == id }

    fun save(ctx: Context, c: Child) = prefs(ctx).edit().putString(c.id, c.toJson().toString()).apply()

    fun remove(ctx: Context, id: String) = prefs(ctx).edit().remove(id).apply()
}
