package com.toddlerlauncher

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * For when there's no Wi-Fi: this device makes its own small private Wi-Fi
 * (Android's "local-only hotspot": no internet, random name and password).
 * The parent app scans a QR code with the details and joins it.
 * It stays on while remote access is on, until switched off or the device restarts.
 */
object DirectHotspot {

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    var ssid = ""
        private set
    var password = ""
        private set
    /** "wpa2" or "wpa3" */
    var security = "wpa2"
        private set

    val on get() = reservation != null

    /** Calls back on the main thread with null when ready, or a message saying what went wrong. */
    fun start(ctx: Context, done: (String?) -> Unit) {
        if (reservation != null) return done(null)
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = r
                    if (Build.VERSION.SDK_INT >= 30) {
                        val c = r.softApConfiguration
                        ssid = c.ssid ?: ""
                        password = c.passphrase ?: ""
                        security = if (c.securityType == 3) "wpa3" else "wpa2" // 3 = WPA3-SAE only
                    } else {
                        @Suppress("DEPRECATION")
                        val c = r.wifiConfiguration
                        @Suppress("DEPRECATION")
                        ssid = c?.SSID?.trim('"') ?: ""
                        @Suppress("DEPRECATION")
                        password = c?.preSharedKey?.trim('"') ?: ""
                        security = "wpa2"
                    }
                    done(null)
                }

                override fun onStopped() {
                    reservation = null
                }

                override fun onFailed(reason: Int) {
                    reservation = null
                    done(
                        when (reason) {
                            ERROR_INCOMPATIBLE_MODE ->
                                "This device's normal hotspot is on (or it can't do both at once). Switch the normal hotspot off and try again."
                            ERROR_TETHERING_DISALLOWED -> "Hotspots are blocked on this device."
                            else -> "Android couldn't start it (code $reason). Try switching Wi-Fi on, then try again."
                        }
                    )
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            done(if (Build.VERSION.SDK_INT >= 33) "Please allow \"Nearby devices\" for Toddler Launcher, then try again."
            else "Please allow location for Toddler Launcher and switch Location on (Android needs it for this), then try again.")
        } catch (e: Exception) {
            done("Android couldn't start it: ${e.message}")
        }
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (e: Exception) {
        }
        reservation = null
    }
}
