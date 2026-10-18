package com.mewname.app.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Construct and resolve on Dispatchers.IO; Coil owns bitmap/network work and caches. */
internal class PersonalCollectionImages(context: Context) {
    private val spriteIndex = JSONObject(context.assets.open("pokemon/go_sprite_index.json").bufferedReader().use { it.readText() })
    private val spriteFiles = spriteIndex.getJSONObject("files")
    internal fun gameSprite(file: String, shiny: Boolean = false): String? {
        val candidate = if (shiny) shinyFile(file) else file
        val path = spriteFiles.optString(candidate).takeIf { it.isNotBlank() } ?: return null
        return "https://raw.githubusercontent.com/PokeMiners/pogo_assets/${spriteIndex.getString("revision")}/" + path.replace(" ", "%20")
    }
    private val gameForms = JSONArray(context.assets.open("pokemon/collection_game_forms.json").bufferedReader().use { it.readText() })
    private val gameImages = (0 until gameForms.length()).associate { index ->
        gameForms.getJSONObject(index).let { it.getString("id") to it.optString("image").takeUnless { value -> value.isBlank() || value == "null" } }
    }
    private val images = JSONObject(context.assets.open("raids/images.json").bufferedReader().use { it.readText() })
    private val names = JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
    private val species = (0 until names.length()).associate { i ->
        names.getJSONObject(i).let { key(it.getString("name")) to it }
    }
    private val exactSprites = JSONObject(context.assets.open("pokemon/pokedex_sprites.json").bufferedReader().use { it.readText() })
    private val speciesAliases = (0 until names.length()).flatMap { i ->
        val entry = names.getJSONObject(i)
        val aliases = entry.optJSONArray("aliases")
        (0 until (aliases?.length() ?: 0)).map { key(aliases!!.getString(it)) to entry }
    }.groupBy({ it.first }, { it.second }).mapNotNull { (alias, entries) ->
        entries.distinctBy { it.getString("name") }.singleOrNull()?.let { alias to it }
    }.toMap()
    private val imageIds = images.keys().asSequence().associateBy { key(it.removeSuffix("_FORM")) }

    /** Exact form ID lookup; never replaces an unknown form with the ordinary species. */
    fun formUrl(id: String): String? = gameImages[id.uppercase(Locale.ROOT)]
        ?.substringAfterLast('/')?.let { gameSprite(it) }

    fun urls(target: PersonalCollectionTarget, group: PersonalCollectionGroup): List<String> {
        if (group != PersonalCollectionGroup.MEGA && group != PersonalCollectionGroup.GIGANTAMAX)
            return listOfNotNull(url(target, group == PersonalCollectionGroup.SHINY))
        if (!PersonalCollection.supports(target, group)) return emptyList()
        val name = JSONArray(target.key).getString(0)
        val dex = species[key(name)]?.getInt("dex") ?: return emptyList()
        val forms = if (group == PersonalCollectionGroup.MEGA) listOf("MEGA", "MEGA_X", "MEGA_Y")
            else listOf("GIGANTAMAX", "AMPED_GIGANTAMAX", "LOW_KEY_GIGANTAMAX")
        return forms.mapNotNull { gameSprite("pm$dex.f$it.icon.png") }
    }

    fun url(target: PersonalCollectionTarget, shiny: Boolean = false): String? {
        val parts = runCatching { JSONArray(target.key) }.getOrNull() ?: return null
        val name = parts.optString(0)
        val variant = parts.optString(1)
        if (variant.startsWith("GAME_COSTUME:")) return gameSprite(variant.removePrefix("GAME_COSTUME:"), shiny)
        if (variant.startsWith("GAME_FORM:")) {
            val form = variant.removePrefix("GAME_FORM:")
            val dex = species[key(name)]?.getInt("dex")
            val prefix = key(name).replace(' ', '_') + "_"
            if (dex != null && form.startsWith(prefix)) {
                gameSprite("pm$dex.f${form.removePrefix(prefix)}.icon.png", shiny)?.let { return it }
            }
            return gameImages[form]?.let { gameSprite(it.substringAfterLast('/'), shiny) }
        }
        val entry = species[key(name)] ?: speciesAliases[key(name)] ?: return null
        val dex = entry.getInt("dex")
        if (variant.isBlank()) {
            val exact = exactSprites.optString(entry.getString("name").uppercase(Locale.ROOT))
            if (exact.isNotBlank()) return gameSprite(exact, shiny)
        }
        // Ordinary species must not inherit costume files accidentally assigned by raid feeds.
        if (!name.contains('(') && (variant.isBlank() || name in setOf("SCATTERBUG", "SPEWPA")))
            return gameSprite("pm$dex.icon.png", shiny) ?: gameSprite("pokemon_icon_${dex.toString().padStart(3, '0')}_00.png", shiny)
                ?: when (dex) {
                    647 -> gameSprite("pm647.fORDINARY.icon.png", shiny)
                    671 -> gameSprite("pm671.fRED.icon.png", shiny)
                    778 -> gameSprite("pm778.fDISGUISED.icon.png", shiny)
                    else -> null
                }
        val candidates = mutableListOf<String>()
        if (variant.isNotBlank() && name !in setOf("SCATTERBUG", "SPEWPA")) {
            val code = UniquePokemonCatalog.optionsFor(name).firstOrNull { key(it.label) == key(variant) }?.code
            val form = when (name) {
                "UNOWN" -> when (variant) { "!" -> "EXCLAMATION_POINT"; "?" -> "QUESTION_MARK"; else -> variant }
                "FURFROU" -> mapOf("DAN" to "DANDY", "DEB" to "DEBUTANTE", "DIA" to "DIAMOND", "HRT" to "HEART", "KAB" to "KABUKI", "LR" to "LA_REINE", "MAT" to "MATRON", "PHA" to "PHARAOH", "PB" to "NATURAL", "STR" to "STAR")[code]
                "ROTOM" -> mapOf("NRM" to "", "FAN" to "FAN", "FRS" to "FROST", "HEA" to "HEAT", "MOW" to "MOW", "WSH" to "WASH")[code]
                "GENESECT" -> mapOf("NRM" to "", "BRN" to "BURN", "CHL" to "CHILL", "DSE" to "DOUSE", "SHK" to "SHOCK")[code]
                "SPINDA" -> code
                else -> variant
            } ?: return null
            candidates += if (form.isBlank()) name else "${name}_${form}_FORM"
        } else {
            candidates += when (dex) { 29 -> "NIDORAN_FEMALE"; 32 -> "NIDORAN_MALE"; else -> name }
            val aliases = entry.optJSONArray("aliases")
            if (aliases != null) for (i in 0 until aliases.length()) candidates += aliases.getString(i)
        }
        for (candidate in candidates) {
            val id = imageIds[key(candidate.removeSuffix("_FORM"))] ?: continue
            val file = images.optString(id)
            // The upstream catalog uses Unown as a placeholder for unavailable species.
            if (file.isNotBlank() && (dex == 201 || file != "pokemon_icon_201_00.png"))
                gameSprite(file.substringAfterLast('/'), shiny)?.let { return it }
        }
        // Never substitute another form or an ordinary sprite for an unavailable variant.
        return null
    }

    private fun shinyFile(file: String) = if (file.endsWith(".icon.png"))
        file.removeSuffix(".icon.png") + ".s.icon.png" else file.removeSuffix(".png") + "_shiny.png"
    private fun key(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .uppercase(Locale.ROOT).replace("'", "").replace("♀", " FEMALE").replace("♂", " MALE")
        .replace(Regex("[^A-Z0-9]+"), " ").trim()
}