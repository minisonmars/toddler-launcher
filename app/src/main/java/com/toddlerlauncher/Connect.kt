package com.toddlerlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.toddlerlauncher.link.Link

/** QR codes for the Toddler Parent app, and "No Wi-Fi? Connect directly". */
object Connect {

    const val PERMISSION_REQUEST = 7701

    /** The address QR: a browser opens the web page, the parent app pairs with it. */
    fun addressUrl(settings: Settings, host: String) = "http://$host:${Link.PORT}/?id=${settings.deviceId}"

    /** What the parent app scans to join this device's private hotspot. */
    fun directUrl(settings: Settings): String {
        val b = Uri.Builder().scheme("toddlerlink").authority("direct")
            .appendQueryParameter("id", settings.deviceId)
            .appendQueryParameter("ssid", DirectHotspot.ssid)
            .appendQueryParameter("pass", DirectHotspot.password)
            .appendQueryParameter("sec", DirectHotspot.security)
            .appendQueryParameter("port", Link.PORT.toString())
        for (a in RemoteServer.addresses()) b.appendQueryParameter("host", a)
        return b.build().toString()
    }

    private fun neededPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION

    /** Starts the private hotspot (asking for Android's permission first if needed), then shows its QR code. */
    fun direct(activity: Activity) {
        val settings = Settings(activity)
        if (!settings.remoteOn) {
            settings.remoteOn = true
            RemoteService.sync(activity)
        }
        if (activity.checkSelfPermission(neededPermission()) != PackageManager.PERMISSION_GRANTED) {
            // Android's permission box is another app: keep Toddler Guard from closing it
            settings.installingUntil = System.currentTimeMillis() + 2 * 60_000L
            activity.requestPermissions(arrayOf(neededPermission()), PERMISSION_REQUEST)
            return
        }
        startAndShow(activity)
    }

    /** Call from the activity's onRequestPermissionsResult. */
    fun onPermissionResult(activity: Activity, requestCode: Int, results: IntArray) {
        if (requestCode != PERMISSION_REQUEST) return
        Settings(activity).installingUntil = 0L
        if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) startAndShow(activity)
        else Ui.toast(activity, "Android needs that permission to make the private hotspot.")
    }

    private fun startAndShow(activity: Activity) {
        DirectHotspot.start(activity) { problem ->
            if (activity.isFinishing) return@start
            if (problem != null) {
                AlertDialog.Builder(activity).setTitle("Couldn't connect directly")
                    .setMessage(problem).setPositiveButton("OK", null).show()
                return@start
            }
            // Give the hotspot a moment to get its address
            activity.window.decorView.postDelayed({ if (!activity.isFinishing) showDirect(activity) }, 1500)
        }
    }

    private fun showDirect(activity: Activity) {
        val settings = Settings(activity)
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8))
        }
        val dm = activity.resources.displayMetrics
        val size = (minOf(dm.widthPixels, dm.heightPixels) - Ui.dp(activity, 120)).coerceAtMost(Ui.dp(activity, 300))
        Qr.bitmap(directUrl(settings), size)?.let { bmp ->
            col.addView(ImageView(activity).apply {
                setImageBitmap(bmp)
                contentDescription = "QR code for the parent app"
            })
        }
        col.addView(TextView(activity).apply {
            text = "Private Wi-Fi: ${DirectHotspot.ssid}\nPassword: ${DirectHotspot.password}"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(activity, 8), 0, 0)
        })
        AlertDialog.Builder(activity)
            .setTitle("Connect directly")
            .setMessage("In the Toddler Parent app on your phone, tap Add a child's device, then Scan QR code. " +
                "Your phone joins this device's private Wi-Fi (no internet on it, your phone keeps its mobile data).")
            .setView(ScrollView(activity).apply { addView(col) })
            .setPositiveButton("Close", null)
            .setNegativeButton("Stop the private Wi-Fi") { _, _ -> DirectHotspot.stop() }
            .show()
    }
}
