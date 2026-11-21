package com.toddlerlauncher

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager

/** Checks and shortcuts for the Android settings that lock the device down. */
object Device {

    fun isDefaultHome(ctx: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == ctx.packageName
    }

    fun isGuardEnabled(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
    }

    fun isBatteryUnrestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun canInstallUpdates(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun isScreenPinned(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** Asks Android to make this the home app (a pop-up on Android 10+, the settings page before that). */
    fun askToBeHome(activity: Activity, settings: Settings) {
        // Android jumps straight to the new home screen once it's chosen, closing this screen,
        // so the home screen reopens it (see MainActivity.onResume)
        settings.returnTo = activity.javaClass.name
        settings.returnUntil = System.currentTimeMillis() + 3 * 60_000L
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    activity.startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 7)
                    return
                } catch (e: Exception) {
                }
            }
        }
        openHomeSettings(activity)
    }

    /** The settings page where the home app is chosen (also used to switch back to the normal home screen). */
    fun openHomeSettings(ctx: Context) {
        open(ctx, Intent(AndroidSettings.ACTION_HOME_SETTINGS)) ||
            open(ctx, Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) ||
            open(ctx, Intent(AndroidSettings.ACTION_SETTINGS))
    }

    fun openAccessibility(ctx: Context) {
        open(ctx, Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) ||
            open(ctx, Intent(AndroidSettings.ACTION_SETTINGS))
    }

    fun openAppInfo(ctx: Context) {
        open(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }

    fun askBatteryUnrestricted(ctx: Context) {
        open(
            ctx,
            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        ) || open(ctx, Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openInstallPermission(ctx: Context) {
        open(ctx, Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
    }

    /** Screen pinning lives under Security (sometimes "Other security settings" or "More security"). */
    fun openSecurity(ctx: Context) {
        open(ctx, Intent(AndroidSettings.ACTION_SECURITY_SETTINGS)) || open(ctx, Intent(AndroidSettings.ACTION_SETTINGS))
    }

    fun open(ctx: Context, intent: Intent): Boolean =
        try {
            if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }

    fun unpin(activity: Activity) {
        try {
            if (isScreenPinned(activity)) activity.stopLockTask()
        } catch (e: Exception) {
        }
    }
}
