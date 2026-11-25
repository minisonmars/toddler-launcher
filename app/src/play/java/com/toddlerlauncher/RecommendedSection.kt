package com.toddlerlauncher

import android.app.Activity

/** The Play version has no Recommended apps section (Play doesn't allow installing apps from GitHub). */
class RecommendedSection(a: Activity, settings: Settings, rebuild: () -> Unit) {
    fun build(rows: Rows) {
        rows.note("Other toddler apps will be listed here once they are on Google Play.")
    }
}
