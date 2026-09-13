package com.mewname.app.domain

import java.util.Locale

/** Consistent form labels, including legacy regional aliases from saved data. */
fun pokemonDisplayName(name: String): String = when (name.trim().uppercase(Locale.ROOT)) {
    "ZACIAN HERO", "ZACIAN HERO OF MANY BATTLES", "ZACIAN (HERO)" -> "Zacian (Hero)"
    "ZACIAN CROWNED SWORD", "CROWNED SWORD ZACIAN", "ZACIAN ESPADA COROADA", "ZACIAN (COROADO)" -> "Zacian (Coroado)"
    "ZAMAZENTA HERO", "ZAMAZENTA HERO OF MANY BATTLES", "ZAMAZENTA (HERO)" -> "Zamazenta (Hero)"
    "ZAMAZENTA CROWNED SHIELD", "CROWNED SHIELD ZAMAZENTA", "ZAMAZENTA ESCUDO COROADO", "ZAMAZENTA (COROADO)" -> "Zamazenta (Coroado)"
    else -> regionalPokemonDisplayName(name)
}
private fun regionalPokemonDisplayName(name: String): String {
    val match = Regex("^(Alolan|Galarian|Hisuian|Paldean)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(name.trim()) ?: return name
    val region = when (match.groupValues[1].lowercase(Locale.ROOT)) {
        "alolan" -> "Alola"
        "galarian" -> "Galar"
        "hisuian" -> "Hisui"
        else -> "Paldea"
    }
    return "${match.groupValues[2]} ($region)"
}