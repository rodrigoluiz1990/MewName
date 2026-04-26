package com.mewname.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.mewname.app.domain.AppLanguage
import com.mewname.app.model.NamingBlock
import com.mewname.app.model.NamingBlockType
import com.mewname.app.model.NamingField

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.PT_BR }

fun lt(
    language: AppLanguage,
    pt: String,
    en: String,
    es: String
): String = when (language) {
    AppLanguage.PT_BR -> pt
    AppLanguage.EN -> en
    AppLanguage.ES -> es
}

@Composable
fun appLanguage(): AppLanguage = LocalAppLanguage.current

fun NamingField.localizedLabel(language: AppLanguage): String = when (this) {
    NamingField.POKEMON_NAME -> lt(language, "Nome", "Name", "Nombre")
    NamingField.UNOWN_LETTER -> lt(language, "Letra Unown", "Unown Letter", "Letra Unown")
    NamingField.UNIQUE_FORM -> lt(language, "Forma Unica", "Unique Form", "Forma Unica")
    NamingField.VIVILLON_PATTERN -> lt(language, "Padrao Vivillon", "Vivillon Pattern", "Patron Vivillon")
    NamingField.POKEDEX_NUMBER -> lt(language, "Nº Pokedex", "Pokedex No.", "Nº Pokedex")
    NamingField.CP -> "CP"
    NamingField.IV_PERCENT -> lt(language, "IV Medio", "Average IV", "IV Medio")
    NamingField.IV_COMBINATION -> "IV A/D/S"
    NamingField.LEVEL -> lt(language, "Nivel", "Level", "Nivel")
    NamingField.GENDER -> lt(language, "Genero", "Gender", "Genero")
    NamingField.TYPE -> lt(language, "Tipo", "Type", "Tipo")
    NamingField.FAVORITE -> lt(language, "Favorito", "Favorite", "Favorito")
    NamingField.LUCKY -> lt(language, "Sortudo", "Lucky", "Suerte")
    NamingField.SHADOW -> lt(language, "Sombrio", "Shadow", "Oscuro")
    NamingField.PURIFIED -> lt(language, "Purificado", "Purified", "Purificado")
    NamingField.SPECIAL_BACKGROUND -> lt(language, "Fundo Especial", "Special Background", "Fondo Especial")
    NamingField.ADVENTURE_EFFECT -> lt(language, "Efeito Aventura", "Adventure Effect", "Efecto Aventura")
    NamingField.SIZE -> lt(language, "Tamanho", "Size", "Tamano")
    NamingField.MASTER_IV_BADGE -> "IV Master"
    NamingField.PVP_LEAGUE -> lt(language, "Liga PvP", "PvP League", "Liga PvP")
    NamingField.PVP_RANK -> lt(language, "Ranking PvP", "PvP Rank", "Ranking PvP")
    NamingField.LEGACY_MOVE -> lt(language, "Ataque Legado", "Legacy Move", "Ataque Legado")
    NamingField.LEGACY_MOVE_NAME -> lt(language, "Nome Ataque Legado", "Legacy Move Name", "Nombre Ataque Legado")
    NamingField.EVOLUTION_TYPE -> lt(language, "Evolucao", "Evolution", "Evolucion")
}

fun NamingBlock.localizedLabel(language: AppLanguage): String = when (type) {
    NamingBlockType.VARIABLE -> field?.localizedLabel(language) ?: lt(language, "Campo", "Field", "Campo")
    NamingBlockType.FIXED_TEXT -> if (fixedText.isBlank()) lt(language, "Texto fixo", "Fixed text", "Texto fijo") else "\"$fixedText\""
}

fun appLanguageLabel(language: AppLanguage, target: AppLanguage): String = when (target) {
    AppLanguage.PT_BR -> lt(language, "Portugues", "Portuguese", "Portugues")
    AppLanguage.EN -> "English"
    AppLanguage.ES -> "Español"
}

fun appLanguageFlag(target: AppLanguage): String = when (target) {
    AppLanguage.PT_BR -> "🇧🇷"
    AppLanguage.EN -> "🇺🇸"
    AppLanguage.ES -> "🇪🇸"
}
