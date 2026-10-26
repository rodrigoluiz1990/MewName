package com.mewname.app.domain

import java.net.URI
import java.time.LocalDateTime

/** Only categories with a complete rotation schedule are authoritative here.
 * Max Mondays and raid hours are special windows, not a weekly boss roster.
 */
internal fun scheduledRaidChoices(
    catalog: List<RaidChoice>, events: List<CalendarEvent>, now: LocalDateTime
): List<RaidChoice> {
    val scheduledTiers = mapOf("raid" to "RAID_LEVEL_5", "raid_mega" to "RAID_LEVEL_MEGA")
    val active = events.mapNotNull { event ->
        val tier = scheduledTiers[event.category] ?: return@mapNotNull null
        if (now.isBefore(event.start) || !now.isBefore(event.end)) return@mapNotNull null
        val uri = runCatching { URI(event.link.orEmpty()) }.getOrNull() ?: return@mapNotNull null
        if (uri.host !in setOf("pokebattler.com", "www.pokebattler.com")) return@mapNotNull null
        val path = uri.path.orEmpty().trim('/').split('/')
        if (path.size < 2 || path[0] != "raids") return@mapNotNull null
        val id = path[1].takeIf { it.matches(Regex("[A-Z0-9_]+")) } ?: return@mapNotNull null
        RaidChoice(id, tier)
    }
    // Missing/offline schedules must never promote undated simulator entries to "current".
    // Scheduled bosses can be absent from the simulator feed during a regional rollover.
    return (catalog.filterNot { it.tier in scheduledTiers.values } + active).distinct()
}
