package com.toddlerlauncher.parent

import android.content.Context
import android.os.Build
import com.toddlerlauncher.link.Link
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A problem to show the parent, in plain words. */
class LinkError(msg: String, val notPaired: Boolean = false) : Exception(msg)

/** Talking to Toddler Launcher. All of these block, so call them off the main thread. */
object Net {

    /** Plain request. Returns (status code, JSON reply). */
    fun post(host: String, port: Int, path: String, body: JSONObject, timeoutMs: Int = 5000): Pair<Int, JSONObject> {
        val url = URL("http://$host:$port$path")
        // Over the launcher's private hotspot, talk through that network
        val net = Direct.network
        val conn = (if (net != null && Direct.isOnHotspot(host)) net.openConnection(url) else url.openConnection()) as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = 15_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            val bytes = body.toString().toByteArray()
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            return code to (try { JSONObject(text) } catch (e: Exception) { JSONObject() })
        } finally {
            conn.disconnect()
        }
    }

    /** Checks there's a Toddler Launcher at that address, and returns its id and name. */
    fun hello(host: String, port: Int): JSONObject? = try {
        val (code, o) = post(host, port, "/api/hello", JSONObject(), 3000)
        if (code == 200 && o.optString("app") == "toddler-launcher") o else null
    } catch (e: Exception) {
        null
    }

    /** Pairs with the launcher at that address using the parent PIN. Returns the saved child. */
    fun pair(ctx: Context, host: String, port: Int, pin: String): Pair<Child, String> {
        val kp = Link.newKeyPair()
        val myPub = Link.publicKey(kp)
        val (c1, r1) = try {
            post(host, port, "/api/pair/start", JSONObject().put("pub", myPub))
        } catch (e: IOException) {
            throw LinkError("Couldn't reach the toddler device. Is remote access on, and are you on the same Wi-Fi?")
        }
        if (c1 != 200) throw LinkError(r1.optString("error", "The toddler device refused ($c1). Is it up to date?"))
        val childPub = r1.getString("pub")
        val key = Link.sharedKey(kp.private, childPub, myPub, childPub)
        val phone = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).take(40)
        val box = Link.seal(key, JSONObject().put("pin", pin).put("phone", phone).toString())
        val (c2, r2) = post(host, port, "/api/pair/finish", JSONObject().put("session", r1.getString("session")).put("box", box))
        if (c2 != 200) throw LinkError(r2.optString("error", "Pairing failed ($c2)"))
        val reply = try {
            JSONObject(Link.open(key, r2.getJSONObject("box")))
        } catch (e: Exception) {
            throw LinkError("Pairing failed: the reply didn't check out. Please try again.")
        }
        val child = Child(reply.getString("id"), reply.optString("name", "Toddler device"), reply.getString("pair"), key, host, port)
        Store.get(ctx, child.id)?.let { old -> child.ssid = old.ssid; child.pass = old.pass; child.sec = old.sec }
        Store.save(ctx, child)
        return child to Link.checkCode(key)
    }

    /**
     * Sends a locked request to a paired child and returns the unlocked reply.
     * Tries the address the finder last saw, then the last one that worked.
     */
    fun call(ctx: Context, child: Child, path: String, body: JSONObject = JSONObject()): JSONObject {
        val hosts = LinkedHashSet<String>()
        Finder.found[child.id]?.let { hosts.add(it.host) }
        if (child.host.isNotEmpty()) hosts.add(child.host)
        if (Direct.network != null && Direct.ssid == child.ssid) hosts.addAll(Direct.hosts)
        if (hosts.isEmpty()) throw LinkError("Can't find ${child.name} on this network.")
        for (h in hosts) {
            val port = Finder.found[child.id]?.takeIf { it.host == h }?.port ?: child.port
            try {
                val inner = JSONObject().put("path", path).put("body", body).put("ts", System.currentTimeMillis())
                val (code, r) = post(h, port, "/api/s", JSONObject().put("pair", child.pair).put("box", Link.seal(child.key, inner.toString())))
                if (code == 403) throw LinkError("${child.name} doesn't know this phone any more. Remove it and pair again.", notPaired = true)
                if (code != 200) throw LinkError(r.optString("error", "Error $code"))
                val reply = JSONObject(Link.open(child.key, r.getJSONObject("box")))
                if (h != child.host || port != child.port) {
                    child.host = h
                    child.port = port
                    Store.save(ctx, child)
                }
                val inBody = reply.optJSONObject("body") ?: JSONObject()
                if (reply.optInt("code") != 200) throw LinkError(inBody.optString("error", "Error ${reply.optInt("code")}"))
                return inBody
            } catch (e: LinkError) {
                throw e
            } catch (e: Exception) {
                // try the next address
            }
        }
        throw LinkError("Can't reach ${child.name}. Check it's on, remote access is on, and you're on the same Wi-Fi.")
    }
}
