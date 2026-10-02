package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Canonical move names, suitable for the existing review selectors. */
internal data class RecognizedPokemonMoves(val fast: String? = null, val charged: String? = null)

internal object PokemonMoveRecognition {
    @Volatile private var aliases: Map<String, Set<String>>? = null

    fun read(context: Context, pokemonName: String?, lines: List<String>): RecognizedPokemonMoves {
        if (pokemonName.isNullOrBlank() || lines.isEmpty()) return RecognizedPokemonMoves()
        val moves = PokemonMoveRepository.load(context, pokemonName, AppLanguage.EN)
        val labels = aliases ?: synchronized(this) {
            aliases ?: run {
                val json = JSONObject(context.assets.open("catalogs/move_labels.json").bufferedReader().use { it.readText() })
                buildMap<String, Set<String>> {
                    json.keys().forEach { name ->
                        val translations = json.getJSONObject(name)
                        put(name, translations.keys().asSequence().map { translations.getString(it) }.toSet())
                    }
                }.also { aliases = it }
            }
        }
        return match(lines, moves, labels)
    }

    internal fun match(
        lines: List<String>, moves: PokemonMoveSet, labels: Map<String, Set<String>>
    ): RecognizedPokemonMoves {
        // Exact, accent-insensitive matching only: partial words must not choose another move.
        val candidates = lines.map { normalize(it.replace(Regex("""\s+\d{1,3}\s*$"""), "")) }
        fun find(options: List<PokemonMove>): String? {
            val names = options.associate { move ->
                move.name to (labels[move.name].orEmpty() + move.name + move.localizedName).map(::normalize).toSet()
            }
            return candidates.firstNotNullOfOrNull { candidate ->
                names.filterValues { candidate.isNotEmpty() && candidate in it }.keys.singleOrNull()
            }
        }
        return RecognizedPokemonMoves(find(moves.fastMoves), find(moves.chargedMoves))
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9]+"), " ").trim()
}
