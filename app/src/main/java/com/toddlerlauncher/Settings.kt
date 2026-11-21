package com.toddlerlauncher

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/** All the parent-controlled options, saved on the device and applied straight away. */
class Settings(ctx: Context) {

    private val p = ctx.applicationContext.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private fun getB(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()
    private fun getS(k: String, d: String) = p.getString(k, d) ?: d
    private fun putS(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun getI(k: String, d: Int) = p.getInt(k, d)
    private fun putI(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun getL(k: String, d: Long) = p.getLong(k, d)
    private fun putL(k: String, v: Long) = p.edit().putLong(k, v).apply()

    // ---------- Allowed apps, in the order they are shown ----------
    var allowed: List<String>
        get() = getS("allowed", "").split(',').filter { it.isNotBlank() }
        set(v) = putS("allowed", v.joinToString(","))

    fun isAllowed(pkg: String) = allowed.contains(pkg)

    // ---------- Home screen layout ----------
    var columns: Int
        get() = getI("columns", 3)
        set(v) = putI("columns", v)
    var rows: Int
        get() = getI("rows", 4)
        set(v) = putI("rows", v)
    var showNames: Boolean
        get() = getB("showNames", true)
        set(v) = putB("showNames", v)
    var iconSize: String // "small", "medium", "large"
        get() = getS("iconSize", "large")
        set(v) = putS("iconSize", v)
    var orientation: String // "auto", "portrait", "landscape"
        get() = getS("orientation", "auto")
        set(v) = putS("orientation", v)
    var hideBars: Boolean // hide the status and navigation bars on the toddler screen
        get() = getB("hideBars", true)
        set(v) = putB("hideBars", v)
    var keepScreenOn: Boolean
        get() = getB("keepScreenOn", false)
        set(v) = putB("keepScreenOn", v)

    // ---------- Wallpaper ----------
    var wallpaper: String // a colour name from WALLPAPERS, or "photo"
        get() = getS("wallpaper", "sky")
        set(v) = putS("wallpaper", v)
    var dimPhoto: Boolean
        get() = getB("dimPhoto", true)
        set(v) = putB("dimPhoto", v)

    // ---------- Parent button ----------
    var gearCorner: String // "tr", "tl", "br", "bl"
        get() = getS("gearCorner", "tr")
        set(v) = putS("gearCorner", v)
    var holdSeconds: Int
        get() = getI("holdSeconds", 3)
        set(v) = putI("holdSeconds", v)
    var showUpdateOnHome: Boolean // handy while the app is being built
        get() = getB("showUpdateOnHome", true)
        set(v) = putB("showUpdateOnHome", v)

    // ---------- Protection ----------
    var guardOn: Boolean // whether Toddler Guard enforces, when it is switched on in Android
        get() = getB("guardOn", true)
        set(v) = putB("guardOn", v)
    var pinScreen: Boolean // screen pinning on the toddler home screen
        get() = getB("pinScreen", false)
        set(v) = putB("pinScreen", v)
    /** A parent screen to reopen after Android jumps to the home screen (when choosing the home app). */
    var returnTo: String
        get() = getS("returnTo", "")
        set(v) = putS("returnTo", v)
    var returnUntil: Long
        get() = getL("returnUntil", 0L)
        set(v) = putL("returnUntil", v)
    var setupShown: Boolean
        get() = getB("setupShown", false)
        set(v) = putB("setupShown", v)

    /** While a parent is in the parent area (or Android settings opened from it) the guard stands aside. */
    var parentUntil: Long
        get() = getL("parentUntil", 0L)
        set(v) = putL("parentUntil", v)

    /** Protection paused by the parent until this time (Long.MAX_VALUE = until switched back on). */
    var pausedUntil: Long
        get() = getL("pausedUntil", 0L)
        set(v) = putL("pausedUntil", v)

    /** Another home app to use for a while (its component name), and until when. */
    var otherHome: String
        get() = getS("otherHome", "")
        set(v) = putS("otherHome", v)
    var otherHomeUntil: Long
        get() = getL("otherHomeUntil", 0L)
        set(v) = putL("otherHomeUntil", v)

    fun usingOtherHome() = otherHome.isNotEmpty() && System.currentTimeMillis() < otherHomeUntil

    fun stopOtherHome() {
        otherHome = ""
        otherHomeUntil = 0L
        pausedUntil = 0L
    }

    /** Try to stop the notification shade being pulled down at all (an invisible strip over the status bar). */
    var blockPullDown: Boolean
        get() = getB("blockPullDown", true)
        set(v) = putB("blockPullDown", v)

    /** While an app update is being installed, the guard stands aside so Android's installer and
     *  Play Protect pop-ups aren't closed (they can appear one after another). */
    var installingUntil: Long
        get() = getL("installingUntil", 0L)
        set(v) = putL("installingUntil", v)

    fun isInstalling() = System.currentTimeMillis() < installingUntil

    fun startParentMode() {
        parentUntil = System.currentTimeMillis() + PARENT_MINUTES * 60_000L
    }

    fun endParentMode() {
        parentUntil = 0L
    }

    fun inParentMode() = System.currentTimeMillis() < parentUntil
    fun isPaused() = System.currentTimeMillis() < pausedUntil

    // ---------- Unlock: PIN or pattern (stored as salted hashes, never the code itself) ----------
    var lockType: String // "pin" or "pattern"
        get() = if (getS("lockType", "pin") == "pattern" && hasPattern()) "pattern" else "pin"
        set(v) = putS("lockType", v)

    fun checkPin(pin: String): Boolean {
        val salt = p.getString("pinSalt", null)
        val hash = p.getString("pinHash", null)
        if (salt == null || hash == null) return pin == DEFAULT_PIN
        return sha(salt + pin) == hash
    }

    fun isDefaultPin() = p.getString("pinHash", null) == null

    fun setPin(pin: String) {
        val salt = randomSalt()
        p.edit().putString("pinSalt", salt).putString("pinHash", sha(salt + pin)).apply()
    }

    fun hasPattern() = p.getString("patHash", null) != null

    /** A pattern is the dots in the order they were joined, for example "0,1,2,5". */
    fun checkPattern(dots: List<Int>): Boolean {
        val salt = p.getString("patSalt", null) ?: return false
        val hash = p.getString("patHash", null) ?: return false
        return sha(salt + dots.joinToString(",")) == hash
    }

    fun setPattern(dots: List<Int>) {
        val salt = randomSalt()
        p.edit().putString("patSalt", salt).putString("patHash", sha(salt + dots.joinToString(","))).apply()
    }

    fun hasRecoveryWord(): Boolean = p.getString("recHash", null) != null

    fun setRecoveryWord(word: String) {
        val salt = randomSalt()
        p.edit().putString("recSalt", salt).putString("recHash", sha(salt + normalise(word))).apply()
    }

    fun checkRecoveryWord(word: String): Boolean {
        val salt = p.getString("recSalt", null) ?: return false
        val hash = p.getString("recHash", null) ?: return false
        return sha(salt + normalise(word)) == hash
    }

    /** Back to PIN 0000, and the pattern is removed (the recovery word is kept). */
    fun resetUnlock() {
        p.edit().remove("pinSalt").remove("pinHash").remove("patSalt").remove("patHash")
            .putString("lockType", "pin").apply()
    }

    private fun normalise(s: String) = s.trim().lowercase()

    private fun randomSalt(): String {
        val b = ByteArray(8)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // ---------- Backup and restore ----------

    /** Every saved setting as JSON (the PIN and pattern stay as hashes). */
    fun exportJson(): String {
        val o = JSONObject()
        for ((k, v) in p.all) {
            if (k == "parentUntil" || k == "pausedUntil" || k.startsWith("return") || k == "installingUntil" || k.startsWith("otherHome")) continue
            val item = JSONObject()
            when (v) {
                is Boolean -> item.put("t", "b").put("v", v)
                is Int -> item.put("t", "i").put("v", v)
                is Long -> item.put("t", "l").put("v", v)
                is String -> item.put("t", "s").put("v", v)
                else -> continue
            }
            o.put(k, item)
        }
        return JSONObject().put("app", "toddler-launcher").put("settings", o).toString(2)
    }

    /** Returns false if the file isn't a Toddler Launcher backup. */
    fun importJson(text: String): Boolean {
        val root = JSONObject(text)
        if (root.optString("app") != "toddler-launcher") return false
        val o = root.getJSONObject("settings")
        val e = p.edit()
        for (k in o.keys()) {
            val item = o.getJSONObject(k)
            when (item.getString("t")) {
                "b" -> e.putBoolean(k, item.getBoolean("v"))
                "i" -> e.putInt(k, item.getInt("v"))
                "l" -> e.putLong(k, item.getLong("v"))
                "s" -> e.putString(k, item.getString("v"))
            }
        }
        e.apply()
        return true
    }

    companion object {
        const val DEFAULT_PIN = "0000"
        const val PARENT_MINUTES = 15

        /** name to (label, top colour, bottom colour) */
        val WALLPAPERS = listOf(
            "sky" to Triple("Sky blue", Color.rgb(120, 200, 255), Color.rgb(40, 120, 230)),
            "grass" to Triple("Grass green", Color.rgb(150, 225, 120), Color.rgb(40, 150, 80)),
            "sunshine" to Triple("Sunshine", Color.rgb(255, 230, 120), Color.rgb(255, 150, 40)),
            "pink" to Triple("Candy pink", Color.rgb(255, 190, 220), Color.rgb(230, 90, 150)),
            "purple" to Triple("Purple", Color.rgb(200, 160, 255), Color.rgb(110, 60, 200)),
            "sea" to Triple("Deep sea", Color.rgb(60, 200, 200), Color.rgb(20, 70, 140)),
            "night" to Triple("Night", Color.rgb(50, 60, 110), Color.rgb(10, 10, 30)),
            "plain" to Triple("Plain grey", Color.rgb(90, 90, 95), Color.rgb(90, 90, 95)),
        )

        val ICON_SIZES = listOf("small" to "Small", "medium" to "Medium", "large" to "Large")
        val ORIENTATIONS = listOf(
            "auto" to "Turn with the device", "portrait" to "Always upright (portrait)",
            "landscape" to "Always sideways (landscape)"
        )
        val CORNERS = listOf(
            "tr" to "Top right", "tl" to "Top left", "br" to "Bottom right", "bl" to "Bottom left"
        )
        val HOLD_TIMES = listOf("1" to "1 second", "2" to "2 seconds", "3" to "3 seconds", "5" to "5 seconds")
        val GRID_SIZES = (1..6).map { it.toString() to it.toString() }
        val LOCK_TYPES = listOf("pin" to "PIN code", "pattern" to "Pattern")
    }
}
