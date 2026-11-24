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

    val ALL = listOf(
        Def("checks", "Lock-down checks", Icon.CHECKLIST, GREEN),
        Def("apps", "Apps and app time limits", Icon.GRID, BLUE),
        Def("recommended", "Recommended apps", Icon.STAR, ORANGE),
        Def("time", "Screen time and allowed hours", Icon.CLOCK, PURPLE),
        Def("report", "Usage report", Icon.CHART, TEAL),
        Def("look", "Look and layout", Icon.PALETTE, PINK),
        Def("code", "Parent code and button", Icon.LOCK, ORANGE),
        Def("protect", "Protection and volume", Icon.SHIELD, GREEN),
        Def("connect", "Wi-Fi, mobile data and Bluetooth", Icon.WIFI, BLUE),
        Def("remote", "Remote access", Icon.REMOTE, PURPLE),
        Def("updates", "Updates and backup", Icon.DOWNLOAD, TEAL),
        Def("device", "This device", Icon.DEVICE, GREY),
    )

    fun get(key: String) = ALL.firstOrNull { it.key == key }
}
