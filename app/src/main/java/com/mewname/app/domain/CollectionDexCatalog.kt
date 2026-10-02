package com.mewname.app.domain

import android.content.Context
import com.mewname.app.model.PokemonScreenData
import com.mewname.app.model.VivillonPattern
import org.json.JSONArray
import java.text.Normalizer

internal data class CollectionSpecies(val dex: Int, val name: String, val targets: List<PersonalCollectionTarget>, val costumeKeys: Set<String> = emptySet())
internal object CollectionDexCatalog {
    private fun id(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").uppercase(java.util.Locale.ROOT)
        .replace(Regex("[^A-Z0-9]"), "")
    fun load(context: Context, saved: Collection<PersonalCollectionTarget> = emptyList()): List<CollectionSpecies> {
        val names = JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
        val game = JSONArray(context.assets.open("pokemon/collection_game_forms.json").bufferedReader().use { it.readText() })
        val costumeKeys = mutableSetOf<String>()
        val groups = linkedMapOf<Int, MutableList<PersonalCollectionTarget>>()
        val labels = mutableMapOf<Int, String>()
        val known = mutableSetOf<String>()
        val dexByName = mutableMapOf<String, Int>()
        val uniqueDex = mutableSetOf<Int>()
        for (i in 0 until names.length()) {
            val entry = names.getJSONObject(i)
            val name = entry.getString("name")
            val dex = entry.getInt("dex")
            labels.putIfAbsent(dex, name)
            known.add(id(name)); dexByName[id(name)] = dex
            val aliases = entry.optJSONArray("aliases")
            if (aliases != null) for (a in 0 until aliases.length()) known.add(id(aliases.getString(a)))
            val list = groups.getOrPut(dex) { mutableListOf() }
            val data = PokemonScreenData(pokemonName = name)
            val options = UniquePokemonCatalog.optionsFor(name)
            when {
                name.uppercase() in setOf("SCATTERBUG", "SPEWPA", "VIVILLON") -> {
                    uniqueDex.add(dex)
                    VivillonPattern.entries.forEach { pattern -> PersonalCollection.target(context, data.copy(vivillonPattern = pattern))?.let(list::add) }
                }
                options.isNotEmpty() -> {
                    uniqueDex.add(dex)
                    options.forEach { form -> PersonalCollection.target(context, data.copy(uniqueForm = form.label,
                        unownLetter = if (name.equals("Unown", true)) form.label else null))?.let(list::add) }
                }
                else -> PersonalCollection.target(context, data)?.let(list::add)
            }
        }
        for (i in 0 until game.length()) {
            val form = game.getJSONObject(i)
            val dex = form.getInt("dex")
            val code = form.getString("id")
            val base = labels[dex] ?: continue
            if (id(code) in known || (dex in uniqueDex && !form.optBoolean("costume"))) continue
            val suffix = code.substringAfter('_', code).lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
            val target = PersonalCollectionTarget(JSONArray(listOf(base.uppercase(java.util.Locale.ROOT), "GAME_FORM:$code")).toString(), "$base · $suffix")
            groups.getOrPut(dex) { mutableListOf() }.add(target)
            if (form.optBoolean("costume") || dex == 25 || code.endsWith("_COPY_2019") || code.endsWith("_GOGGLES_2026")) costumeKeys.add(target.key)
        }
        val costumes = JSONArray(context.assets.open("pokemon/go_costumes.json").bufferedReader().use { it.readText() })
        for (i in 0 until costumes.length()) {
            val costume = costumes.getJSONObject(i)
            val dex = costume.getInt("dex")
            val base = labels[dex] ?: continue
            val target = PersonalCollectionTarget(JSONArray(listOf(base.uppercase(java.util.Locale.ROOT),
                "GAME_COSTUME:" + costume.getString("file"))).toString(), "$base · ${costume.getString("label")}")
            groups.getOrPut(dex) { mutableListOf() }.add(target)
            costumeKeys.add(target.key)
        }
        for (target in saved) {
            val name = runCatching { JSONArray(target.key).getString(0) }.getOrNull() ?: continue
            dexByName[id(name)]?.let { groups.getOrPut(it) { mutableListOf() }.add(target) }
        }
        return groups.map { (dex, targets) -> CollectionSpecies(dex, labels.getValue(dex), targets.distinctBy { it.key }, costumeKeys.intersect(targets.map { it.key }.toSet())) }.sortedBy { it.dex }
    }
}
