package com.toddlerlauncher.parent

import android.content.Context
import com.toddlerlauncher.link.Link
import org.json.JSONObject

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
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("pair", pair).put("key", Link.b64(key))
        .put("host", host).put("port", port).put("ssid", ssid).put("pass", pass).put("sec", sec)

    companion object {
        fun fromJson(o: JSONObject) = Child(
            o.getString("id"), o.optString("name"), o.getString("pair"), Link.unb64(o.getString("key")),
            o.optString("host"), o.optInt("port", Link.PORT), o.optString("ssid"), o.optString("pass"), o.optString("sec")
        )
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
