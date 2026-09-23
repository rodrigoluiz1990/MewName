package com.mewname.app

import com.mewname.app.domain.RaidHistoryCategory
import java.util.Locale

internal fun raidCategoryTier(category: RaidHistoryCategory): String = canonicalRaidTier(category.id)

private fun canonicalRaidTier(value: String): String {
    val id = value.removePrefix("live_").uppercase(Locale.US).removeSuffix("_LEGACY")
    return when (id) {
        "RAIDS5" -> "RAID_LEVEL_5"
        "MEGA" -> "RAID_LEVEL_MEGA"
        "MEGASUPER" -> "RAID_LEVEL_4_MEGA_ENHANCED"
        "SHADOW" -> "RAID_LEVEL_5_SHADOW"
        "GIGANTAMAX" -> "RAID_LEVEL_6_MAX"
        else -> id
    }
}

internal fun orderedRaidCategories(categories: List<RaidHistoryCategory>): List<RaidHistoryCategory> {
    fun tier(category: RaidHistoryCategory) = raidCategoryTier(category)
    fun group(category: RaidHistoryCategory): Int {
        val id = tier(category)
        return when {
            "MEGA" in id -> 1
            "ULTRA_BEAST" in id -> 2
            "SHADOW" in id -> 3
            "ELITE" in id -> 4
            isMaxRaidCategory(category) -> 5
            else -> 0
        }
    }
    fun level(category: RaidHistoryCategory) =
        Regex("^RAID_LEVEL_(?:MEGA_)?(\\d+(?:_\\d+)?)").find(tier(category))
            ?.groupValues?.get(1)?.replace('_', '.')?.toDoubleOrNull() ?: 0.0
    return categories.sortedWith(compareBy<RaidHistoryCategory> { group(it) }
        .thenBy { if (group(it) == 1 && tier(it) == "RAID_LEVEL_MEGA") 0 else 1 }
        .thenBy { level(it) }.thenBy { tier(it) })
}

/** Confirmed shield encounters through 2026-09-21; sources in docs/raids-super-mega.md.
 * Repairs historical feeds that still use old Mega simulation tiers.
 * Live encounters use their explicit API tier instead of guessing from species.
 */
internal val savedSuperMegaBosses = setOf(
    "VICTREEBEL_MEGA", "MALAMAR_MEGA", "DRAGONITE_MEGA", "FALINKS_MEGA",
    "RAICHU_MEGA_X", "RAICHU_MEGA_Y", "STARMIE_MEGA",
    "MEWTWO_MEGA_X", "MEWTWO_MEGA_Y", "STARAPTOR_MEGA"
)
private val heavyMegaBosses = setOf(
    "GROUDON_PRIMAL", "KYOGRE_PRIMAL", "LATIAS_MEGA", "LATIOS_MEGA",
    "RAYQUAZA_MEGA", "MEWTWO_MEGA_X", "MEWTWO_MEGA_Y"
)
internal fun raidHasShields(tier: String) = "MEGA_ENHANCED" in tier.uppercase(Locale.US)

internal fun raidBattleTier(item: com.mewname.app.domain.RaidHistoryItem, category: RaidHistoryCategory): String =
    item.battleTier ?: Regex("RAID_LEVEL_[A-Z0-9_]+").find(item.url)?.value
        ?: if (raidHistorySpriteId(item) in heavyMegaBosses) "RAID_LEVEL_MEGA_5"
        else if (category.id.equals("megaSuper", true)) "RAID_LEVEL_MEGA"
        else raidCategoryTier(category)

/** Merge UI groups without changing the provider's battle tier used for counter calculations. */
internal fun normalizeRaidCategories(categories: List<RaidHistoryCategory>, saved: Boolean): List<RaidHistoryCategory> {
    val normalized = categories.flatMap { category ->
        category.items.map { item ->
            val battleTier = raidBattleTier(item, category)
            val mega = "MEGA" in raidCategoryTier(category)
            val groupTier = when {
                saved && mega && raidHistorySpriteId(item) in savedSuperMegaBosses -> "RAID_LEVEL_4_MEGA_ENHANCED"
                raidHasShields(battleTier) -> "RAID_LEVEL_4_MEGA_ENHANCED"
                mega -> "RAID_LEVEL_MEGA"
                category.id.equals("dynamax", true) -> battleTier
                else -> raidCategoryTier(category)
            }
            category.copy(id = (if (saved) "" else "live_") + groupTier,
                items = listOf(item.copy(battleTier = battleTier)))
        }
    }
    return orderedRaidCategories(normalized.groupBy { it.id }.map { (_, matches) ->
        matches.first().copy(items = matches.flatMap { it.items }.distinctBy { raidHistorySpriteId(it) })
    })
}

internal fun mergeSavedRaidCategories(
    bundled: List<RaidHistoryCategory>,
    downloaded: List<RaidHistoryCategory>
): List<RaidHistoryCategory> = normalizeRaidCategories(bundled + downloaded, saved = true)