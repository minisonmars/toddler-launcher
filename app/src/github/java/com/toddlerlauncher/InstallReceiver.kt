package com.toddlerlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** Receives the result of an install and shows Android's confirmation screen when needed. */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirm)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                val label = intent.getStringExtra(LABEL) ?: ""
                if (label.isEmpty()) {
                    Toast.makeText(context, "Updated. Press Home to go back to Toddler Launcher.", Toast.LENGTH_LONG).show()
                } else {
                    Settings(context).installingUntil = 0L
                    Toast.makeText(context, "$label is installed", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                Settings(context).installingUntil = 0L // protection straight back on
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"
                Toast.makeText(context, "Install failed: $why", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_INTENT)

    companion object {
        const val LABEL = "label"
    }
}
