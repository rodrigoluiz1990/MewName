package com.mewname.app

internal data class BubbleAppShortcut(
    val key: String, val icon: String, val pt: String, val en: String, val es: String,
    val screen: AppScreen? = null, val url: String? = null
)
internal val bubbleAppShortcuts = listOf(
    BubbleAppShortcut("app_calendar", "calendar", "Calendário", "Calendar", "Calendario",
        screen = AppScreen.CALENDAR),
    BubbleAppShortcut("app_names", "names", "Definir nomes", "Name presets", "Definir nombres", AppScreen.PRESET_LIST),
    BubbleAppShortcut("app_filters", "filters", "Filtros", "Filters", "Filtros", AppScreen.FILTER_BUILDER),
    BubbleAppShortcut("app_pokedex", "pokedex", "Pokédex", "Pokédex", "Pokédex", AppScreen.POKEDEX),
    BubbleAppShortcut("app_collections", "collections", "Coleções", "Collections", "Colecciones", AppScreen.COLLECTIONS),
    BubbleAppShortcut("app_raid", "raid", "Raids", "Raids", "Raids", AppScreen.RAID_PLANNER),
    BubbleAppShortcut("app_rocket", "rocket", "Equipe Rocket", "Team Rocket", "Equipo Rocket", screen = AppScreen.ROCKET),
    BubbleAppShortcut("app_research", "research", "Pesquisas", "Research", "Investigaciones", screen = AppScreen.RESEARCH),
    BubbleAppShortcut("app_eggs", "eggs", "Ovos", "Eggs", "Huevos", screen = AppScreen.EGGS),
    BubbleAppShortcut("app_adventure", "adventure", "Efeitos de Aventura", "Adventure Effects", "Efectos de Aventura", AppScreen.ADVENTURE_EFFECTS),
    BubbleAppShortcut("app_legacy", "legacy", "Ataques legados", "Legacy moves", "Ataques legado", AppScreen.LEGACY_MOVES),
    BubbleAppShortcut("app_moves", "charged_moves", "Ataques", "Moves", "Ataques", AppScreen.MOVEDEX),
    BubbleAppShortcut("app_promo", "promo", "Códigos promocionais", "Promo codes", "Códigos promocionales", screen = AppScreen.PROMO_CODES),
    BubbleAppShortcut("app_types", "types", "Tipos", "Types", "Tipos", AppScreen.TYPE_CHART),
    BubbleAppShortcut("app_profile", "names", "Perfil", "Profile", "Perfil", AppScreen.TRAINER_PROFILE)
)