package com.mewname.app.domain

import java.util.Locale

/** UI form labels; canonical asset keys and aliases remain unchanged. */
fun pokemonDisplayName(name: String): String = when (name.trim().uppercase(Locale.ROOT)) {
    "ZACIAN HERO", "ZACIAN HERO OF MANY BATTLES", "ZACIAN (HERO)" -> "Zacian (Hero)"
    "ZACIAN CROWNED SWORD", "CROWNED SWORD ZACIAN", "ZACIAN ESPADA COROADA", "ZACIAN (COROADO)" -> "Zacian (Coroado)"
    "ZAMAZENTA HERO", "ZAMAZENTA HERO OF MANY BATTLES", "ZAMAZENTA (HERO)" -> "Zamazenta (Hero)"
    "ZAMAZENTA CROWNED SHIELD", "CROWNED SHIELD ZAMAZENTA", "ZAMAZENTA ESCUDO COROADO", "ZAMAZENTA (COROADO)" -> "Zamazenta (Coroado)"
    else -> name
}