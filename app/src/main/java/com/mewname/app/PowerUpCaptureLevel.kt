package com.mewname.app

/**
 * Source-level lookup deviations from the published capture/additional CPM dataset.
 * Compared against the float32 PLAYER_LEVEL_SETTINGS thresholds, not an arbitrary discount.
 * Source: https://docs.google.com/spreadsheets/d/1HFB490ZZp_jK4mlE_rZ1g5AlQmcvZEv-qOzFyip1ZcU/edit
 * Research: https://www.reddit.com/r/TheSilphRoad/comments/1scwzhs/
 * Checked 2026-10-26. Covers integer capture levels 1..35 and paid levels below 50.
 * Unknown capture levels retain the nominal estimate. Species and trade status cannot infer it.
 */
internal fun powerUpCostLookupLevel(level: Double, captureLevel: Int?): Double {
    if (captureLevel == null || captureLevel !in 1..35 || captureLevel > level) return level
    val previousBand = when (level) {
        7.0, 8.0, 9.0, 12.0 -> captureLevel == 1
        13.0 -> captureLevel == 1 || captureLevel == 3
        35.0 -> captureLevel == 4
        37.0 -> captureLevel in setOf(4, 7, 8, 9, 12, 13)
        else -> false
    }
    return if (previousBand) level - 1.0 else level
}
