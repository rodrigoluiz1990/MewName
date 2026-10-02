package com.mewname.app.domain

import com.mewname.app.model.SpecialBackgroundType
import java.util.Locale

internal enum class BackgroundCategory(val label: String, val type: SpecialBackgroundType) {
    LOCATION("Localidades", SpecialBackgroundType.LOCATION),
    ANNIVERSARY("Aniversário", SpecialBackgroundType.ANNIVERSARY),
    COMMUNITY_DAY("Dia da Comunidade", SpecialBackgroundType.COMMUNITY_DAY),
    GO_FEST("GO Fest", SpecialBackgroundType.GO_FEST),
    GO_TOUR("GO Tour", SpecialBackgroundType.GO_TOUR),
    MEGA_EVOLUTION("Mega Evolução", SpecialBackgroundType.MEGA_EVOLUTION),
    WILD_AREA("Wild Area", SpecialBackgroundType.WILD_AREA),
    SEASON("Temporadas", SpecialBackgroundType.SEASON),
    TEAM_LEADER("Líderes de equipe", SpecialBackgroundType.TEAM_LEADER),
    SPECIAL("Eventos especiais", SpecialBackgroundType.SPECIAL)
}

internal object SpecialBackgroundCatalog {
    const val PATH = "background_refs/location_cards"
    fun files(context: android.content.Context, path: String = PATH): List<String> =
        context.assets.list(path).orEmpty().flatMap { name ->
            val full = "$path/$name"
            when {
                name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".webp", true) -> listOf(full)
                '.' !in name -> files(context, full)
                else -> emptyList()
            }
        }.sorted()

    fun category(file: String): BackgroundCategory {
        val name = file.substringAfterLast('/').lowercase(Locale.ROOT)
        if (name.startsWith("lc_")) return BackgroundCategory.LOCATION
        if (name == "sb_gotour2026_mega.png" || name == "sb_megaevolution.png") return BackgroundCategory.MEGA_EVOLUTION
        val folder = file.substringBeforeLast('/', "").substringAfterLast('/').lowercase(Locale.ROOT)
        val folderCategory = when(folder) {
            "localidades" -> BackgroundCategory.LOCATION
            "aniversario" -> BackgroundCategory.ANNIVERSARY
            "dia_da_comunidade" -> BackgroundCategory.COMMUNITY_DAY
            "go_fest" -> BackgroundCategory.GO_FEST
            "go_tour" -> BackgroundCategory.GO_TOUR
            "mega_evolucao" -> BackgroundCategory.MEGA_EVOLUTION
            "wild_area" -> BackgroundCategory.WILD_AREA
            "temporadas" -> BackgroundCategory.SEASON
            "lideres_de_equipe" -> BackgroundCategory.TEAM_LEADER
            "eventos_especiais" -> BackgroundCategory.SPECIAL
            else -> null
        }
        if (folderCategory != null) return folderCategory
        return when {
            name == "sb_megaevolution.png" || name == "sb_gotour2026_mega.png" -> BackgroundCategory.MEGA_EVOLUTION
            "anniversary" in name -> BackgroundCategory.ANNIVERSARY
            "community" in name || "cdrecap" in name -> BackgroundCategory.COMMUNITY_DAY
            "gofest" in name -> BackgroundCategory.GO_FEST
            "gotour" in name -> BackgroundCategory.GO_TOUR
            "gowa" in name || "wildarea" in name -> BackgroundCategory.WILD_AREA
            "season" in name -> BackgroundCategory.SEASON
            "teamleader" in name -> BackgroundCategory.TEAM_LEADER
            else -> BackgroundCategory.SPECIAL
        }
    }
    fun type(file: String): SpecialBackgroundType =
        if (file.substringAfterLast('/').equals("sb_GoTour2026_mega.png", true)) SpecialBackgroundType.MEGA_EVOLUTION
        else category(file).type
}
