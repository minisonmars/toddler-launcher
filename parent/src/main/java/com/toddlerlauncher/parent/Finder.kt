package com.toddlerlauncher.parent

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.toddlerlauncher.link.Link
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds Toddler Launcher devices on the same Wi-Fi by themselves, using Android's
 * network service discovery (mDNS). Some routers (guest networks, some mesh systems)
 * block this, so a QR code scan is the backup.
 */
object Finder {

    class Found(val id: String, val name: String, val host: String, val port: Int)

    /** Device id to where it was found. */
    val found = ConcurrentHashMap<String, Found>()

    private var nsd: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicast: WifiManager.MulticastLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val watchers = mutableListOf<() -> Unit>()

    // Android resolves one service at a time, so queue them
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun watch(w: () -> Unit) { watchers.add(w) }
    fun unwatch(w: () -> Unit) { watchers.remove(w) }
    private fun changed() = main.post { watchers.toList().forEach { it() } }

    fun start(ctx: Context) {
        if (listener != null) return
        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicast = wm.createMulticastLock("ToddlerParent").apply {
            setReferenceCounted(false)
            acquire()
        }
        val n = app.getSystemService(NsdManager::class.java)
        nsd = n
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { listener = null }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                synchronized(queue) { queue.add(info) }
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val gone = found.values.filter { it.name == info.serviceName }
                // Only forget it if it doesn't answer any more
                Thread {
                    for (f in gone) if (Net.hello(f.host, f.port) == null) found.remove(f.id)
                    changed()
                }.start()
            }
        }
        try {
            n.discoverServices(Link.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
            listener = l
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            listener?.let { nsd?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
        }
        listener = null
        try {
            multicast?.release()
        } catch (e: Exception) {
        }
        multicast = null
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val info = synchronized(queue) {
            if (resolving || queue.isEmpty()) return
            resolving = true
            queue.removeFirst()
        }
        try {
            nsd?.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) {
                    resolving = false
                    resolveNext()
                }

                override fun onServiceResolved(i: NsdServiceInfo) {
                    val host = i.host
                    if (host is Inet4Address) {
                        val addr = host.hostAddress ?: ""
                        val port = i.port
                        val idAttr = i.attributes["id"]?.let { String(it) } ?: ""
                        // Check it really is a Toddler Launcher, and get its current name
                        Thread {
                            val hi = Net.hello(addr, port)
                            if (hi != null) {
                                val id = hi.optString("id", idAttr)
                                found[id] = Found(id, hi.optString("name", i.serviceName), addr, port)
                                changed()
                            }
                        }.start()
                    }
                    resolving = false
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            resolving = false
        }
    }
}
