package com.toddlerlauncher

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.toddlerlauncher.link.Link
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A small web page served on the local network (same Wi-Fi or hotspot), so a
 * parent can change settings from their own phone's browser. Run by Toddler Guard.
 * Log in with the parent PIN; a wrong PIN 5 times locks the page for 5 minutes.
 */
class RemoteServer(private val ctx: Context) {

    private val settings = Settings(ctx)
    private val usage = Usage(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var server: ServerSocket? = null
    private val sessions = HashMap<String, Long>()
    private var wrong = 0
    private var lockedUntil = 0L

    /** Pairings in progress: session id to (our key pair, parent's public key, expiry). */
    private class PendingPair(val kp: java.security.KeyPair, val parentPub: String, val expires: Long)
    private val pending = HashMap<String, PendingPair>()

    /** Recently seen message ivs, so a copied message can't be sent again. */
    private val seen = LinkedHashMap<String, Long>()

    val running get() = server != null

    fun start() {
        if (server != null) return
        val s = try {
            ServerSocket(PORT)
        } catch (e: Exception) {
            return
        }
        server = s
        Thread {
            while (!s.isClosed) {
                val client = try {
                    s.accept()
                } catch (e: Exception) {
                    break
                }
                Thread { handle(client) }.start()
            }
        }.start()
    }

    fun stop() {
        try {
            server?.close()
        } catch (e: Exception) {
        }
        server = null
        synchronized(sessions) { sessions.clear() }
    }

    // ---------- HTTP ----------

    private fun handle(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 15_000
                val input = BufferedInputStream(sock.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1].substringBefore('?')
                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val i = line.indexOf(':')
                    if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (length in 1..100_000) {
                    val buf = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(buf, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    String(buf, 0, read)
                } else ""
                val out = sock.getOutputStream()

                when {
                    method == "GET" && (path == "/" || path == "/index.html") -> {
                        val html = ctx.assets.open("remote.html").use { it.readBytes() }
                        send(out, 200, "text/html; charset=utf-8", html)
                    }
                    path.startsWith("/api/") -> api(out, method, path, headers["x-token"] ?: "", body)
                    else -> send(out, 404, "text/plain", "Not found".toByteArray())
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) return null
        }
    }

    private fun send(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = when (code) {
            200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error"
        }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun json(out: OutputStream, o: Any, code: Int = 200) =
        send(out, code, "application/json; charset=utf-8", o.toString().toByteArray())

    private fun error(out: OutputStream, code: Int, msg: String) = json(out, JSONObject().put("error", msg), code)

    // ---------- API ----------

    private fun api(out: OutputStream, method: String, path: String, token: String, body: String) {
        val req = try {
            if (body.isBlank()) JSONObject() else JSONObject(body)
        } catch (e: Exception) {
            JSONObject()
        }

        if (path == "/api/login" && method == "POST") {
            val now = System.currentTimeMillis()
            if (now < lockedUntil) return error(out, 429, "Too many wrong tries. Wait a few minutes.")
            if (!pinOk(req.optString("pin"))) return error(out, 401, "Wrong PIN")
            val t = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            synchronized(sessions) { sessions[t] = now + SESSION_MS }
            settings.remoteSeenAt = now
            return json(out, JSONObject().put("token", t))
        }

        // ---- The parent app: pairing, then locked messages ----
        when (path) {
            "/api/hello" -> return json(out, JSONObject().put("app", "toddler-launcher")
                .put("id", settings.deviceId).put("name", deviceName()))
            "/api/pair/start" -> return pairStart(out, req)
            "/api/pair/finish" -> return pairFinish(out, req)
            "/api/s" -> return secure(out, req)
        }

        // ---- The web page: everything else needs a logged-in session ----
        val now = System.currentTimeMillis()
        val ok = synchronized(sessions) {
            val exp = sessions[token]
            if (exp != null && exp > now) {
                sessions[token] = now + SESSION_MS
                true
            } else false
        }
        if (!ok) return error(out, 401, "Please log in")
        settings.remoteSeenAt = now

        if (path == "/api/logout") {
            synchronized(sessions) { sessions.remove(token) }
            settings.remoteSeenAt = 0L
            return json(out, JSONObject().put("ok", true))
        }
        val (code, o) = route(path, req)
        json(out, o, code)
    }

    /** Checks the parent PIN, with 5 wrong tries locking it for 5 minutes. */
    @Synchronized
    private fun pinOk(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) return false
        if (!settings.checkPin(pin)) {
            wrong++
            if (wrong >= 5) {
                wrong = 0
                lockedUntil = now + 5 * 60_000L
            }
            return false
        }
        wrong = 0
        return true
    }

    /** What the parent app shows for this device. */
    private fun deviceName(): String =
        settings.childName.ifBlank { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL }

    private fun pairStart(out: OutputStream, req: JSONObject) {
        val parentPub = req.optString("pub")
        if (parentPub.isEmpty()) return error(out, 400, "Bad request")
        val kp = Link.newKeyPair()
        val id = Link.randomId()
        val now = System.currentTimeMillis()
        synchronized(pending) {
            pending.entries.removeAll { it.value.expires < now }
            if (pending.size > 20) return error(out, 429, "Too many pairing attempts. Wait a minute.")
            pending[id] = PendingPair(kp, parentPub, now + 2 * 60_000L)
        }
        json(out, JSONObject().put("session", id).put("pub", Link.publicKey(kp))
            .put("id", settings.deviceId).put("name", deviceName()))
    }

    private fun pairFinish(out: OutputStream, req: JSONObject) {
        val p = synchronized(pending) { pending.remove(req.optString("session")) }
            ?: return error(out, 400, "Pairing timed out. Please try again.")
        if (p.expires < System.currentTimeMillis()) return error(out, 400, "Pairing timed out. Please try again.")
        if (System.currentTimeMillis() < lockedUntil) return error(out, 429, "Too many wrong tries. Wait a few minutes.")
        val key = try {
            Link.sharedKey(p.kp.private, p.parentPub, p.parentPub, Link.publicKey(p.kp))
        } catch (e: Exception) {
            return error(out, 400, "Bad request")
        }
        val inner = try {
            JSONObject(Link.open(key, req.getJSONObject("box")))
        } catch (e: Exception) {
            return error(out, 400, "Bad request")
        }
        if (!pinOk(inner.optString("pin"))) return error(out, 401, "Wrong PIN")
        val pairId = Link.randomId()
        val phone = inner.optString("phone").take(40).ifBlank { "A parent's phone" }
        Pairs.add(ctx, pairId, key, phone)
        settings.remoteSeenAt = System.currentTimeMillis()
        toast("Paired with $phone (check code ${Link.checkCode(key)})")
        val reply = JSONObject().put("ok", true).put("pair", pairId).put("id", settings.deviceId).put("name", deviceName())
        json(out, JSONObject().put("box", Link.seal(key, reply.toString())))
    }

    /** A locked message from a paired parent app: unlock, check, do it, lock the answer. */
    private fun secure(out: OutputStream, req: JSONObject) {
        val pairId = req.optString("pair")
        val key = Pairs.key(ctx, pairId) ?: return error(out, 403, "Not paired")
        val box = req.optJSONObject("box") ?: return error(out, 400, "Bad request")
        val inner = try {
            JSONObject(Link.open(key, box))
        } catch (e: Exception) {
            return error(out, 403, "Not paired")
        }
        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - inner.optLong("ts")) > Link.MAX_AGE_MS) {
            return json(out, JSONObject().put("box", Link.seal(key, JSONObject().put("code", 400)
                .put("body", JSONObject().put("error", "The two devices' clocks are too far apart. Check the time on both.")).toString())))
        }
        val iv = box.optString("iv")
        synchronized(seen) {
            seen.entries.removeAll { it.value < now - 2 * Link.MAX_AGE_MS }
            if (seen.containsKey(iv)) return error(out, 400, "Repeated message")
            seen[iv] = now
        }
        Pairs.touch(ctx, pairId)
        settings.remoteSeenAt = now
        val path = inner.optString("path")
        val body = inner.optJSONObject("body") ?: JSONObject()
        val (code, o) = when (path) {
            "/api/unpair" -> {
                Pairs.remove(ctx, pairId)
                settings.remoteSeenAt = 0L
                200 to JSONObject().put("ok", true)
            }
            "/api/bye" -> {
                settings.remoteSeenAt = 0L
                200 to JSONObject().put("ok", true)
            }
            "/api/pin" -> {
                // Changing the PIN from the app also needs the current one
                route(path, body)
            }
            else -> route(path, body)
        }
        json(out, JSONObject().put("box", Link.seal(key, JSONObject().put("code", code).put("body", o).toString())))
    }

    /** The actions both the web page and the parent app can do. */
    private fun route(path: String, req: JSONObject): Pair<Int, JSONObject> {
        fun ok() = 200 to JSONObject().put("ok", true)
        fun err(code: Int, msg: String) = code to JSONObject().put("error", msg)
        return when (path) {
            "/api/state" -> 200 to state()
            "/api/name" -> {
                val name = req.optString("name").trim()
                main.post { settings.childName = name }
                ok()
            }
            "/api/set" -> {
                val key = req.optString("key")
                val item = SCHEMA.firstOrNull { it.key == key } ?: return err(400, "Unknown setting")
                val v: Any = when (item.type) {
                    "bool" -> req.optBoolean("value")
                    "int", "time" -> req.optString("value").toIntOrNull() ?: return err(400, "Bad value")
                    else -> req.optString("value")
                }
                main.post { settings.putRaw(key, v) }
                ok()
            }
            "/api/bonus" -> {
                val min = req.optInt("min", 15).coerceIn(1, 240)
                main.post { settings.addBonus(min) }
                toast("A parent gave $min more minutes")
                ok()
            }
            "/api/lock" -> {
                val on = req.optBoolean("on")
                main.post { settings.lockedNow = on }
                toast(if (on) "Break time" else "Break time is over")
                ok()
            }
            "/api/pause" -> {
                // minutes: 0 = protection back on, -1 = until switched back on
                val min = req.optInt("min", 0)
                main.post {
                    when {
                        min == 0 -> { settings.stopOtherHome() }
                        min < 0 -> { settings.pausedUntil = Long.MAX_VALUE }
                        else -> { settings.pausedUntil = System.currentTimeMillis() + min * 60_000L }
                    }
                }
                ok()
            }
            "/api/app" -> {
                val pkg = req.optString("pkg")
                main.post {
                    if (req.has("allowed")) {
                        val list = settings.allowed.toMutableList()
                        if (req.optBoolean("allowed") && !list.contains(pkg)) list.add(pkg)
                        if (!req.optBoolean("allowed")) list.remove(pkg)
                        settings.allowed = list
                    }
                    if (req.has("limit")) settings.setAppLimit(pkg, req.optInt("limit"))
                }
                ok()
            }
            "/api/pin" -> {
                val cur = req.optString("current")
                val new = req.optString("new")
                when {
                    !pinOk(cur) -> err(400, "The current PIN is wrong")
                    new.length !in 4..8 || new.any { !it.isDigit() } -> err(400, "The new PIN needs 4 to 8 digits")
                    else -> {
                        settings.setPin(new)
                        ok()
                    }
                }
            }
            "/api/wifi/add" -> 200 to JSONObject().put("message", WifiNetworks.add(ctx, req.optString("ssid"), req.optString("password")))
            "/api/wifi/remove" -> 200 to JSONObject().put("message", WifiNetworks.remove(ctx, req.optString("ssid")))
            else -> err(404, "Unknown request")
        }
    }

    private fun toast(msg: String) {
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }

    // ---------- Building the state the page shows ----------

    private fun state(): JSONObject {
        val o = JSONObject()
        o.put("version", Updater.installedVersion(ctx))
        o.put("device", Build.MANUFACTURER + " " + Build.MODEL)
        o.put("defaultPin", settings.isDefaultPin())
        o.put("childName", settings.childName)
        o.put("id", settings.deviceId)
        o.put("name", deviceName())

        // Status
        val block = Rules.block(settings, usage)
        o.put("lockedNow", settings.lockedNow)
        o.put("block", when (block) {
            Rules.Block.LOCKED -> "Break time (locked by you)"
            Rules.Block.HOURS -> "Sleep time (outside allowed hours)"
            Rules.Block.DAILY -> "Time's up for today"
            null -> ""
        })
        o.put("paused", settings.isPaused())
        o.put("pausedText", when {
            !settings.isPaused() -> ""
            settings.pausedUntil == Long.MAX_VALUE -> "Protection is paused until switched back on"
            else -> "Protection is paused until " + java.text.SimpleDateFormat("HH:mm", Locale.UK)
                .format(java.util.Date(settings.pausedUntil))
        })
        o.put("guardEnabled", Device.isGuardEnabled(ctx))
        val today = LocalDate.now()
        o.put("usedToday", Usage.format(usage.appSeconds(today)))
        o.put("allowance", if (settings.dailyLimitMin > 0) Usage.format(Rules.dailyAllowanceSec(settings)) else "No limit")
        o.put("bonus", settings.bonusToday())

        // Settings the page can change
        val sch = JSONArray()
        for (item in SCHEMA) {
            val opts = JSONArray()
            val list = if (item.key == "wallpaper") {
                Settings.WALLPAPERS.map { it.first to it.second.first } +
                    (if (File(ctx.filesDir, MainActivity.WALLPAPER_FILE).exists()) listOf("photo" to "My photo") else emptyList())
            } else item.options
            for ((v, l) in list) opts.put(JSONArray().put(v).put(l))
            sch.put(
                JSONObject().put("key", item.key).put("label", item.label).put("group", item.group)
                    .put("type", item.type).put("options", opts).put("value", item.read(settings).toString())
            )
        }
        o.put("settings", sch)

        // Apps
        val apps = JSONArray()
        val pm = ctx.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val found = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        for ((pkg, label) in found) {
            apps.put(
                JSONObject().put("pkg", pkg).put("label", label).put("allowed", settings.isAllowed(pkg))
                    .put("limit", settings.appLimitMin(pkg)).put("today", Usage.format(usage.seconds(today, pkg)))
            )
        }
        o.put("apps", apps)
        val limits = JSONArray()
        for ((v, l) in Settings.APP_LIMITS) limits.put(JSONArray().put(v).put(l))
        o.put("appLimitOptions", limits)

        // Report
        val days = (0..2).map { today.minusDays(it.toLong()) }
        val week = (0..6).map { today.minusDays(it.toLong()) }
        o.put("reportDays", JSONArray().put("Today").put("Yesterday").put(DateTimeFormatter.ofPattern("EEE", Locale.UK).format(days[2])))
        val names = found.toMap()
        val rows = JSONArray()
        val pkgs = week.flatMap { usage.packages(it) }.toSet().filter { it != ctx.packageName }
            .sortedByDescending { p -> week.sumOf { usage.seconds(it, p) } }
        for (pkg in pkgs) {
            rows.put(JSONObject().put("label", names[pkg] ?: pkg)
                .put("days", JSONArray(days.map { Usage.format(usage.seconds(it, pkg)) }))
                .put("avg", Usage.format(week.sumOf { usage.seconds(it, pkg) } / 7)))
        }
        rows.put(JSONObject().put("label", "All apps").put("total", true)
            .put("days", JSONArray(days.map { Usage.format(usage.appSeconds(it)) }))
            .put("avg", Usage.format(week.sumOf { usage.appSeconds(it) } / 7)))
        o.put("report", rows)

        // Wi-Fi
        o.put("wifiOn", Device.isWifiOn(ctx) ?: false)
        o.put("wifiSupported", Build.VERSION.SDK_INT >= 29)
        o.put("wifiNetworks", JSONArray(WifiNetworks.list(ctx)))
        return o
    }

    class Item(
        val key: String, val label: String, val group: String, val type: String,
        val options: List<Pair<String, String>>, val read: (Settings) -> Any
    )

    companion object {
        const val PORT = 8080
        private const val SESSION_MS = 30 * 60_000L

        private val YES_NO = emptyList<Pair<String, String>>()

        val SCHEMA = listOf(
            Item("dailyLimitMin", "Daily limit (time in apps)", "Screen time", "int", Settings.DAILY_LIMITS) { it.dailyLimitMin },
            Item("warnings", "\"5 minutes left\" warning", "Screen time", "bool", YES_NO) { it.warnings },
            Item("hoursOn", "Only allow use between set times", "Allowed hours", "bool", YES_NO) { it.hoursOn },
            Item("weekdayStart", "Monday to Friday from", "Allowed hours", "time", YES_NO) { it.weekdayStart },
            Item("weekdayEnd", "Monday to Friday until", "Allowed hours", "time", YES_NO) { it.weekdayEnd },
            Item("weekendStart", "Saturday and Sunday from", "Allowed hours", "time", YES_NO) { it.weekendStart },
            Item("weekendEnd", "Saturday and Sunday until", "Allowed hours", "time", YES_NO) { it.weekendEnd },
            Item("guardOn", "Use Toddler Guard", "Protection and volume", "bool", YES_NO) { it.guardOn },
            Item("blockPullDown", "Block pulling down notifications", "Protection and volume", "bool", YES_NO) { it.blockPullDown },
            Item("volumeCap", "Highest media volume", "Protection and volume", "int", Settings.VOLUME_CAPS) { it.volumeCap },
            Item("pinScreen", "Pin the toddler screen", "Protection and volume", "bool", YES_NO) { it.pinScreen },
            Item("nameSpacing", "Space between the name's letters", "Look and layout", "str", Settings.NAME_SPACINGS) { it.nameSpacing },
            Item("nameColours", "Name colours", "Look and layout", "str", Settings.NAME_COLOURS) { it.nameColours },
            Item("nameAction", "When the name is touched", "Look and layout", "str", Settings.NAME_ACTIONS) { it.nameAction },
            Item("columns", "Columns (across)", "Look and layout", "int", Settings.GRID_SIZES) { it.columns },
            Item("rows", "Rows (down)", "Look and layout", "int", Settings.GRID_SIZES) { it.rows },
            Item("swapWhenSideways", "Swap rows and columns when sideways", "Look and layout", "bool", YES_NO) { it.swapWhenSideways },
            Item("iconSize", "Icon size", "Look and layout", "str", Settings.ICON_SIZES) { it.iconSize },
            Item("showNames", "Show app names", "Look and layout", "bool", YES_NO) { it.showNames },
            Item("wallpaper", "Background", "Look and layout", "str", YES_NO) { it.wallpaper },
            Item("dimPhoto", "Darken the photo a little", "Look and layout", "bool", YES_NO) { it.dimPhoto },
            Item("orientation", "Screen direction", "Look and layout", "str", Settings.ORIENTATIONS) { it.orientation },
            Item("hideBars", "Hide the status and navigation bars", "Look and layout", "bool", YES_NO) { it.hideBars },
            Item("keepScreenOn", "Keep the screen on", "Look and layout", "bool", YES_NO) { it.keepScreenOn },
            Item("gearCorner", "Parent button corner", "Parent button", "str", Settings.CORNERS) { it.gearCorner },
            Item("holdSeconds", "Hold the parent button for", "Parent button", "int", Settings.HOLD_TIMES) { it.holdSeconds },
            Item("showQrOnHome", "Connect button (QR code) on the toddler screen", "Parent button", "bool", YES_NO) { it.showQrOnHome },
            Item("showUpdateOnHome", "Update button on the toddler screen", "Parent button", "bool", YES_NO) { it.showUpdateOnHome },
        )

        /** This device's addresses on the local network, for example 192.168.1.23. */
        fun addresses(): List<String> =
            try {
                NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .map { it.hostAddress ?: "" }
                    .filter { it.isNotEmpty() }
            } catch (e: Exception) {
                emptyList()
            }
    }
}

/**
 * Wi-Fi networks added from the remote page. Android (10+) doesn't let apps join
 * a network directly, but they can "suggest" one: Android then joins it by itself
 * whenever it's in range, once the suggestion has been allowed on this device.
 */
object WifiNetworks {

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("wifi", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<String> = prefs(ctx).all.keys.sorted()

    private fun suggestion(ssid: String, password: String): WifiNetworkSuggestion {
        val b = WifiNetworkSuggestion.Builder().setSsid(ssid)
        if (password.isNotEmpty()) b.setWpa2Passphrase(password)
        return b.build()
    }

    fun add(ctx: Context, ssid: String, password: String): String {
        if (Build.VERSION.SDK_INT < 29) return "This needs Android 10 or newer on the toddler device."
        if (ssid.isBlank()) return "Please enter the network name."
        if (password.isNotEmpty() && password.length < 8) return "Wi-Fi passwords are at least 8 characters."
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val status = wm.addNetworkSuggestions(listOf(suggestion(ssid, password)))
        return when (status) {
            WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                prefs(ctx).edit().putString(ssid, password).apply()
                "Added \"$ssid\". The device joins it by itself when it's in range. The first time, Android asks " +
                    "on the toddler device to allow networks suggested by Toddler Launcher: tap Allow there."
            }
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> "\"$ssid\" is already added."
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED ->
                "Android has blocked suggestions from this app. On the toddler device: Settings > Apps > " +
                    "Toddler Launcher > Wi-Fi control (or Special app access), and allow it."
            else -> "Android didn't accept it (code $status)."
        }
    }

    fun remove(ctx: Context, ssid: String): String {
        if (Build.VERSION.SDK_INT < 29) return "This needs Android 10 or newer."
        val password = prefs(ctx).getString(ssid, null) ?: return "Not found."
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.removeNetworkSuggestions(listOf(suggestion(ssid, password)))
        prefs(ctx).edit().remove(ssid).apply()
        return "Removed \"$ssid\"."
    }
}
