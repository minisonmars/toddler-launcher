package com.toddlerlauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.toddlerlauncher.link.Link
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps Toddler Launcher awake while the child is in other apps: Toddler Guard's
 * screen-time clock and limits, and (when switched on) the remote parent page.
 * Some phones (Xiaomi, Redmi and POCO especially) freeze apps that aren't on screen,
 * which stopped the screen-time clock while the child was in an app.
 *
 * Keeps the remote parent page running whatever is on screen. Android limits
 * apps that are in the background (and Xiaomi phones especially), so the page
 * runs in a "foreground service" with a quiet notification, and holds the
 * Wi-Fi awake so it answers straight away.
 */
class RemoteService : Service() {

    private var server: RemoteServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var announcer: NsdManager.RegistrationListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val settings = Settings(this)
        if (!wanted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground(settings.remoteOn)
        if (settings.remoteOn) {
            if (server == null) server = RemoteServer(this)
            server?.start()
            holdLocks()
            announce()
        } else {
            stopRemote()
        }
        return START_STICKY
    }

    private fun stopRemote() {
        try {
            announcer?.let { getSystemService(NsdManager::class.java).unregisterService(it) }
        } catch (e: Exception) {
        }
        announcer = null
        DirectHotspot.stop()
        server?.stop()
        server = null
        try {
            wifiLock?.release()
            wakeLock?.release()
        } catch (e: Exception) {
        }
        wifiLock = null
        wakeLock = null
    }

    private fun goForeground(remote: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Toddler Guard", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps screen time limits and remote access working while your child is in other apps"
                setShowBadge(false)
            }
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(if (remote) "Toddler Guard and remote access are on" else "Toddler Guard is on")
            .setContentText(if (remote) "Use the Toddler Parent app, or the address in the parent area"
                else "Keeping screen time limits working in every app")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (remote || Build.VERSION.SDK_INT < 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            try {
                startForeground(ID, n, type)
            } catch (e: Exception) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            }
        } else {
            startForeground(ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun holdLocks() {
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            else WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock = wm.createWifiLock(mode, "ToddlerLauncher:remote").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ToddlerLauncher:remote").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    /** Tells parent apps on the same network that this device is here (mDNS), so they find it by themselves. */
    private fun announce() {
        if (announcer != null) return
        val settings = Settings(this)
        val info = NsdServiceInfo().apply {
            serviceName = settings.childName.ifBlank { "Toddler Launcher" }.take(40)
            serviceType = Link.SERVICE_TYPE
            port = Link.PORT
            setAttribute("id", settings.deviceId)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { announcer = null }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
        }
        try {
            getSystemService(NsdManager::class.java).registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
            announcer = l
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        try {
            announcer?.let { getSystemService(NsdManager::class.java).unregisterService(it) }
        } catch (e: Exception) {
        }
        announcer = null
        DirectHotspot.stop()
        server?.stop()
        server = null
        try {
            wifiLock?.release()
            wakeLock?.release()
        } catch (e: Exception) {
        }
        wifiLock = null
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "remote"
        private const val ID = 42

        /** Runs while Toddler Guard is switched on in Android or remote access is on. */
        fun wanted(ctx: Context): Boolean {
            val s = Settings(ctx)
            return s.remoteOn || (s.guardOn && Device.isGuardEnabled(ctx))
        }

        /** Starts, updates or stops the service to match the settings. Safe to call often. */
        fun sync(ctx: Context) {
            try {
                val i = Intent(ctx, RemoteService::class.java)
                if (wanted(ctx)) ctx.startForegroundService(i) else ctx.stopService(i)
            } catch (e: Exception) {
                // Android may refuse to start it from the background; it starts next time the launcher shows
            }
        }
    }
}
