package com.toddlerlauncher

import android.graphics.Color

/**
 * The sections of the parent area, in order, with their icons and colours.
 * Shared so the launcher's parent area, the Toddler Parent app and the remote
 * web page all show the same menu.
 */
object Sections {
    class Def(val key: String, val title: String, val icon: Icon, val colour: Int)

    val GREEN = Color.rgb(52, 179, 122)
    val BLUE = Color.rgb(50, 130, 255)
    val ORANGE = Color.rgb(255, 150, 30)
    val GREY = Color.rgb(140, 140, 140)
    val PURPLE = Color.rgb(140, 90, 230)
    val TEAL = Color.rgb(20, 160, 170)
    val PINK = Color.rgb(230, 80, 150)
    val RED = Color.rgb(225, 60, 60)

    // Everyday things at the top; the set-up-once and safety sections towards the bottom
    val ALL = listOf(
        Def("apps", "Apps and app time limits", Icon.GRID, BLUE),
        Def("time", "Screen time and allowed hours", Icon.CLOCK, PURPLE),
        Def("report", "Usage report", Icon.CHART, TEAL),
        Def("recommended", "Recommended apps", Icon.STAR, ORANGE),
        Def("volume", "Volume", Icon.SPEAKER, ORANGE),
        Def("look", "Look and layout", Icon.PALETTE, PINK),
        Def("connect", "Wi-Fi, mobile data and Bluetooth", Icon.WIFI, BLUE),
        Def("updates", "Updates and backup", Icon.DOWNLOAD, TEAL),
        Def("code", "Parent code and button", Icon.LOCK, ORANGE),
        Def("remote", "Remote access", Icon.REMOTE, PURPLE),
        Def("protect", "Protection and lock-down checks", Icon.SHIELD, GREEN),
        Def("device", "This device", Icon.DEVICE, GREY),
        // Older launchers still send this one; it's now part of Protection
        Def("checks", "Lock-down checks", Icon.CHECKLIST, GREEN),
    )

    fun get(key: String) = ALL.firstOrNull { it.key == key }
}
