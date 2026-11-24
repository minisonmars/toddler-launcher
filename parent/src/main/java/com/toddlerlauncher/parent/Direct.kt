package com.toddlerlauncher.parent

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Handler
import android.os.Looper

/**
 * Joins Toddler Launcher's private hotspot ("No Wi-Fi? Connect directly").
 * Android asks once to confirm. The phone keeps its mobile data for everything
 * else: only this app talks over the hotspot.
 */
object Direct {

    @Volatile var network: Network? = null
        private set
    var ssid = ""
        private set
    var hosts: List<String> = emptyList()
        private set
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun isOnHotspot(host: String) = network != null && hosts.contains(host)

    /** done(null) when joined, or done(problem). Called on the main thread. */
    fun join(ctx: Context, ssid: String, pass: String, sec: String, hostList: List<String>, done: (String?) -> Unit) {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        if (network != null && this.ssid == ssid) {
            hosts = hostList
            return done(null)
        }
        leave(ctx)
        val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
            if (sec == "wpa3") setWpa3Passphrase(pass) else setWpa2Passphrase(pass)
        }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        val main = Handler(Looper.getMainLooper())
        var answered = false
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) {
                network = n
                this@Direct.ssid = ssid
                hosts = hostList
                if (!answered) { answered = true; main.post { done(null) } }
            }

            override fun onLost(n: Network) {
                if (network == n) network = null
            }

            override fun onUnavailable() {
                network = null
                if (!answered) { answered = true; main.post { done("Couldn't join the toddler device's private Wi-Fi. Make sure it's still showing the QR code, and that this phone's Wi-Fi is on.") } }
            }
        }
        callback = cb
        try {
            cm.requestNetwork(request, cb, 60_000)
        } catch (e: Exception) {
            callback = null
            done("Android wouldn't join it: ${e.message}")
        }
    }

    fun leave(ctx: Context) {
        val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
        try {
            callback?.let { cm.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
        }
        callback = null
        network = null
        ssid = ""
        hosts = emptyList()
    }
}
