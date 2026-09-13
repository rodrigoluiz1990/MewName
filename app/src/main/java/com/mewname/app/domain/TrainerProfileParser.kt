package com.mewname.app.domain

import java.text.Normalizer

internal data class TrainerProfileDraft(
    val name: String? = null,
    val level: String? = null,
    val team: String? = null,
    val friendCode: String? = null
)

internal data class TrainerProfileLine(val text: String, val left: Float, val top: Float)

internal object TrainerProfileParser {
    fun isTrainerScreen(text: String): Boolean {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
        val friendship = Regex("""codigo\s+(?:de\s+)?(?:treinador|entrenador)|trainer\s+code""").containsMatchIn(normalized) &&
            Regex("""codigo\s+qr|qr\s+code|compartilhar\s+codigo|share\s+code|compartir\s+codigo""").containsMatchIn(normalized)
        val profile = Regex("""\b(eu|me|yo)\b""").containsMatchIn(normalized) &&
            Regex("""\b(amigos|amgos|friends|social)\b""").containsMatchIn(normalized) &&
            Regex("""total de atividades|total activities|total de actividades|customizar|customize|personalizar""").containsMatchIn(normalized)
        return friendship || profile
    }
    fun parse(text: String, regions: List<TrainerProfileLine> = emptyList()): TrainerProfileDraft {
        val lines = text.lines().map(String::trim).filter(String::isNotEmpty)
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        fun labeled(labels: String, value: String): String? =
            Regex("(?im)^\\s*(?:$labels)\\s*[:：]?\\s*($value)\\s*$")
                .find(normalized)?.groupValues?.get(1)?.trim()
        val code = Regex("(?<!\\d)(\\d{4}[ -]\\d{4}[ -]\\d{4}|\\d{12})(?!\\d)")
            .findAll(text).map { it.groupValues[1].filter(Char::isDigit) }
            .filter { it != "000000000000" }.distinct().toList().singleOrNull()
        val friendship = Regex("""(?i)(codigo\s+(?:de\s+)?(?:treinador|entrenador)|trainer\s+code)""").containsMatchIn(normalized) &&
            Regex("""(?i)(codigo\s+qr|qr\s+code|compartilhar\s+codigo|share\s+code|compartir\s+codigo)""").containsMatchIn(normalized)
        val friendshipName = if (friendship) {
            val candidates = regions.filter { it.top in .15f.. .30f }
                .map { it.text.trim() }.filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{2,19}")) }
            candidates.distinct().singleOrNull() ?: lines.zipWithNext().firstOrNull { (name, next) ->
                name.matches(Regex("[A-Za-z][A-Za-z0-9_]{2,19}")) && next.filter(Char::isDigit) == code
            }?.first
        } else null
        val explicitName = labeled("nome(?: do treinador)?|trainer name|nombre(?: del entrenador)?", "[A-Za-z0-9_]{3,20}")
        val explicitLevel = labeled("nivel|level|lv\\.?", "\\d{1,3}")
            ?.takeIf { it.toInt() in 1..100 }
        val profileScreen = Regex("(?i)\\b(amigos|amgos|friends)\\b").containsMatchIn(normalized) &&
            Regex("(?i)\\b(eu|me|yo|social)\\b").containsMatchIn(normalized)
        val excluded = setOf("AMIGOS", "AMGOS", "FRIENDS", "SOCIAL", "ONLINE", "AVATAR", "STYLE", "ESTILO",
            "EU", "ME", "YO", "CUSTOMIZAR", "CUSTOMIZE", "PERSONALIZAR", "ALBUM", "DIARIO", "JOURNAL", "TIENDA", "SHOP", "LOJA", "NOTICIAS", "NEWS", "ACTIVIDADES",
            "ACTIVITIES", "TOTAL", "POKEMON", "DISTANCIA", "DISTANCE", "NIVEL", "LEVEL", "EXPERIENCE")
        // Infer unlabeled identity only on the trainer tab, adjacent to a standalone level.
        val candidates = if (profileScreen) lines.take(14).mapIndexedNotNull { index, line ->
            val level = line.toIntOrNull()?.takeIf { it in 1..100 } ?: return@mapIndexedNotNull null
            val neighbors = listOfNotNull(lines.getOrNull(index - 1))
                .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{2,19}")) && it.uppercase() !in excluded }
            neighbors.singleOrNull()?.let { it to level.toString() }
        }.distinct() else emptyList()
        val identity = candidates.singleOrNull()
        val trainerTab = profileScreen &&
            !Regex("""(?i)adicionar amigos|add friends|agregar amigos|anadir amigos""").containsMatchIn(normalized)
        fun validName(value: String) =
            value.matches(Regex("[A-Za-z][A-Za-z0-9_]{2,19}")) && value.uppercase() !in excluded
        // The trainer name is under the tabs, not beside the level. The buddy
        // nickname and the friends count must not be mistaken for this identity.
        val headerName = if (trainerTab) {
            val regional = regions.filter { it.left < .5f && it.top in .10f.. .25f }
                .map { it.text.trim() }.filter(::validName).distinct().singleOrNull()
            regional ?: lines.take(14).firstOrNull(::validName)
        } else null
        val bottomLevel = if (trainerTab) {
            Regex("""(?im)^\s*(\d{1,3})\s*(?:NIVEL|LEVEL)\s*$""")
                .find(normalized)?.groupValues?.get(1)?.takeIf { it.toInt() in 1..100 }
        } else null
        val regionLevel = if (trainerTab) regions.filter {
            it.left < .25f && it.top in .35f.. .70f && it.text.trim().matches(Regex("""\d{1,3}"""))
        }.map { it.text.trim() }.filter { it.toInt() in 1..100 }.distinct().singleOrNull() else null
        val team = labeled("equipe|team|equipo", "mystic|valor|instinct|sabedoria|valor|instinto")
        return TrainerProfileDraft(explicitName ?: friendshipName ?: headerName ?: identity?.first,
            explicitLevel ?: bottomLevel ?: regionLevel ?: identity?.second, team, code)
    }
}