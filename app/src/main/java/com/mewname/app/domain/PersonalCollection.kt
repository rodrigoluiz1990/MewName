package com.mewname.app.domain

import android.content.Context
import com.mewname.app.model.PokemonScreenData
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class PersonalCollectionGroup(val label: String, val shortLabel: String) {
    SHINY("Brilhantes", "✨"), LUCKY("Sortudos", "Sortudo"), SHADOW("Sombrosos", "Sombroso"),
    PURIFIED("Purificados", "Purificado"),
    IV100("100%", "100%"), IV98("98%", "98%"), IV96("96%", "96%"), IV93("93%", "93%"),
    IV91("91%", "91%"), IV67("67%", "67%"), IV0("0%", "0%"),
    XXL("XXL", "XXL"), XXS("XXS", "XXS"), BACKGROUND("Fundo especial", "Fundo"),
    LITTLE("PVP · Copinha", "CP"), GREAT("PVP · Great League", "GL"),
    ULTRA("PVP · Ultra League", "UL"), MASTER_LEAGUE("PVP · Master League", "ML"),
    DYNAMAX("Dinamax", "Dinamax"), GIGANTAMAX("Gigamax", "Gigamax"), MEGA("Mega", "Mega"), NORMAL("Normal", "Normal")
}

data class PersonalCollectionTarget(val key: String, val label: String)
data class PersonalCollectionEntry(val target: PersonalCollectionTarget, val groups: Set<PersonalCollectionGroup>)
data class PersonalCollectionSnapshot(
    val enabled: Set<PersonalCollectionGroup>,
    val entries: Map<String, PersonalCollectionEntry>,
    val showInSuggestedNames: Boolean = false,
    val order: List<PersonalCollectionGroup> = PersonalCollectionGroup.entries.toList()
)

object PersonalCollection {
    private val names = PvpRankCalculator()
    fun preferences(context: Context) = context.getSharedPreferences("personal_collection_v1", Context.MODE_PRIVATE)
    private fun normalized(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").uppercase(Locale.ROOT)

    fun target(context: Context, data: PokemonScreenData): PersonalCollectionTarget? {
        val name = data.pokemonName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val canonical = names.canonicalName(context, name)
        val variant = when (normalized(canonical)) {
            "SCATTERBUG", "SPEWPA", "VIVILLON" -> data.vivillonPattern?.name
            "UNOWN" -> data.unownLetter ?: data.uniqueForm
            else -> data.uniqueForm
        }?.trim()?.takeIf { it.isNotEmpty() }
        if (variant == null && (normalized(canonical) in setOf("SCATTERBUG", "SPEWPA", "VIVILLON") || UniquePokemonCatalog.optionsFor(canonical).isNotEmpty())) return null
        // Length-independent JSON parts prevent names and variants from colliding.
        val key = JSONArray(listOf(normalized(canonical), variant?.let(::normalized).orEmpty())).toString()
        val displayVariant = if (normalized(canonical) in setOf("SCATTERBUG", "SPEWPA", "VIVILLON")) data.vivillonPattern?.label else variant
        val label = canonical + (displayVariant?.let { " · $it" } ?: "")
        return PersonalCollectionTarget(key, label)
    }

    fun read(context: Context): PersonalCollectionSnapshot {
        val prefs = preferences(context)
        val enabled = prefs.getStringSet("groups", null)?.mapNotNull { raw ->
            PersonalCollectionGroup.entries.firstOrNull { it.name == raw }
        }?.toSet() ?: PersonalCollectionGroup.entries.toSet()
        val entries = linkedMapOf<String, PersonalCollectionEntry>()
        val json = runCatching { JSONArray(prefs.getString("entries", "[]")) }.getOrElse { JSONArray() }
        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            val key = item.optString("key").takeIf { it.isNotBlank() } ?: continue
            val groupArray = item.optJSONArray("groups") ?: continue
            val groups = (0 until groupArray.length()).mapNotNull { index ->
                PersonalCollectionGroup.entries.firstOrNull { it.name == groupArray.optString(index) }
            }.toSet()
            if (groups.isEmpty()) continue
            entries[key] = PersonalCollectionEntry(PersonalCollectionTarget(key,item.optString("label")), groups)
        }
        val savedOrder = runCatching { JSONArray(prefs.getString("group_order", "[]")) }.getOrElse { JSONArray() }
        val order = normalizeOrder((0 until savedOrder.length()).mapNotNull { index ->
            PersonalCollectionGroup.entries.firstOrNull { it.name == savedOrder.optString(index) }
        })
        return PersonalCollectionSnapshot(enabled, entries, prefs.getBoolean("show_in_suggested_names", false), order)
    }

    private fun normalizeOrder(order: List<PersonalCollectionGroup>): List<PersonalCollectionGroup> =
        (order + PersonalCollectionGroup.entries).distinct()

    fun setOrder(context: Context, order: List<PersonalCollectionGroup>) {
        preferences(context).edit().putString("group_order", JSONArray(normalizeOrder(order).map { it.name }).toString()).apply()
    }

    fun supports(target: PersonalCollectionTarget?, group: PersonalCollectionGroup): Boolean {
        if (group != PersonalCollectionGroup.MEGA && group != PersonalCollectionGroup.GIGANTAMAX) return true
        if (target != null && runCatching { JSONArray(target.key).optString(1).let { it.startsWith("GAME_FORM:") || it.startsWith("GAME_COSTUME:") } }.getOrDefault(false)) return false
        val name = target?.let { runCatching { JSONArray(it.key).getString(0) }.getOrNull() } ?: return false
        return name in if (group == PersonalCollectionGroup.MEGA) CollectionTransformations.mega else CollectionTransformations.gigantamax
    }

    fun setShowInSuggestedNames(context: Context, show: Boolean) {
        preferences(context).edit().putBoolean("show_in_suggested_names", show).apply()
    }

    @Synchronized fun setEnabled(context: Context, group: PersonalCollectionGroup, enabled: Boolean) {
        val current = read(context).enabled
        preferences(context).edit().putStringSet("groups", (if (enabled) current + group else current - group).map { it.name }.toSet()).apply()
    }

    @Synchronized fun mark(context: Context, target: PersonalCollectionTarget, group: PersonalCollectionGroup, obtained: Boolean) {
        if (obtained && !supports(target, group)) return
        val entries = read(context).entries.toMutableMap()
        val old = entries[target.key]?.groups.orEmpty()
        val groups = if (obtained) old + group else old - group
        if (groups.isEmpty()) entries.remove(target.key) else entries[target.key] = PersonalCollectionEntry(target,groups)
        val json = JSONArray()
        entries.values.forEach { entry ->
            json.put(JSONObject().put("key",entry.target.key).put("label",entry.target.label)
                .put("groups",JSONArray(entry.groups.map { it.name })))
        }
        preferences(context).edit().putString("entries",json.toString()).apply()
    }
}