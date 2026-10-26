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
        val entries = readEntries(context, prefs)
        val savedOrder = runCatching { JSONArray(prefs.getString("group_order", "[]")) }.getOrElse { JSONArray() }
        val order = normalizeOrder((0 until savedOrder.length()).mapNotNull { index ->
            PersonalCollectionGroup.entries.firstOrNull { it.name == savedOrder.optString(index) }
        })
        return PersonalCollectionSnapshot(enabled, entries, prefs.getBoolean("show_in_suggested_names", false), order)
    }

    private data class CachedEntries(val raw: Map<String, String>, val entries: Map<String, PersonalCollectionEntry>)
    private val entryCache = java.util.WeakHashMap<android.content.SharedPreferences, CachedEntries>()

    @Synchronized
    private fun readEntries(context: Context, prefs: android.content.SharedPreferences): Map<String, PersonalCollectionEntry> {
        val keys = prefs.getStringSet("entry_keys", null)
        val raw = if (keys == null) mapOf("legacy" to prefs.getString("entries", "[]").orEmpty())
            else keys.associateWith { prefs.getString("entry:" + it, "").orEmpty() }
        val cached = entryCache[prefs]
        if (cached?.raw == raw) return cached.entries
        val entries = linkedMapOf<String, PersonalCollectionEntry>()
        if (keys == null) {
            val array = runCatching { JSONArray(raw.getValue("legacy")) }.getOrElse { JSONArray() }
            for (i in 0 until array.length()) {
                decodeEntry(array.optJSONObject(i))?.let { entry ->
                    val target = canonicalRegionalTarget(context, entry.target)
                    entries[target.key] = PersonalCollectionEntry(target, entries[target.key]?.groups.orEmpty() + entry.groups)
                }
            }
        } else {
            raw.forEach { (key, value) ->
                val entry = decodeEntry(runCatching { JSONObject(value) }.getOrNull())
                if (entry != null) {
                    val target = canonicalRegionalTarget(context, entry.target)
                    entries[target.key] = PersonalCollectionEntry(target, entries[target.key]?.groups.orEmpty() + entry.groups)
                }
            }
        }
        // Keep stable entry identities after merging aliases, so unrelated rows do not reload.
        entries.keys.toList().forEach { key ->
            cached?.entries?.get(key)?.takeIf { it == entries[key] }?.let { entries[key] = it }
        }
        entryCache[prefs] = CachedEntries(raw, entries)
        return entries
    }

    /** Merge legacy regional game IDs with their ordinary catalog keys, preserving captured flags. */
    internal fun canonicalRegionalTarget(context: Context, target: PersonalCollectionTarget): PersonalCollectionTarget {
        val aliased = CollectionFormAliases.resolve(context, target)
        if (aliased.key != target.key) return aliased
        val parts = runCatching { JSONArray(target.key) }.getOrNull() ?: return aliased
        val code = parts.optString(1).removePrefix("GAME_FORM:").replace("_GALARIAN_STANDARD", "_GALARIAN")
        if (!parts.optString(1).startsWith("GAME_FORM:")) return aliased
        val match = Regex("^(.+)_(GALARIAN|GALAR|ALOLAN|ALOLA|HISUIAN|HISUI|PALDEAN|PALDEA)(_ZEN)?$").matchEntire(code) ?: return aliased
        val region = when (match.groupValues[2]) {
            "GALARIAN", "GALAR" -> "Galar"
            "ALOLAN", "ALOLA" -> "Alola"
            "HISUIAN", "HISUI" -> "Hisui"
            else -> "Paldea"
        }
        val name = match.groupValues[1].replace('_', ' ') + " (" + region +
            (if (match.groupValues[3].isNotEmpty()) " Zen" else "") + ")"
        val canonical = names.canonicalName(context, name)
        // Only fold an ID if the name resolver recognizes its exact regional form.
        if (canonical == name) return aliased
        return PersonalCollectionTarget(JSONArray(listOf(normalized(canonical), "")).toString(), canonical)
    }

    private fun decodeEntry(item: JSONObject?): PersonalCollectionEntry? {
        item ?: return null
        val key = item.optString("key").takeIf { it.isNotBlank() } ?: return null
        val array = item.optJSONArray("groups") ?: return null
        val groups = (0 until array.length()).mapNotNull { index ->
            PersonalCollectionGroup.entries.firstOrNull { it.name == array.optString(index) }
        }.toSet()
        if (groups.isEmpty()) return null
        return PersonalCollectionEntry(PersonalCollectionTarget(key, item.optString("label")), groups)
    }

    private fun encodeEntry(entry: PersonalCollectionEntry): String =
        JSONObject().put("key", entry.target.key).put("label", entry.target.label)
            .put("groups", JSONArray(entry.groups.map { it.name })).toString()
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
        val current = preferences(context).getStringSet("groups", null)?.mapNotNull { raw -> PersonalCollectionGroup.entries.firstOrNull { it.name == raw } }?.toSet() ?: PersonalCollectionGroup.entries.toSet()
        preferences(context).edit().putStringSet("groups", (if (enabled) current + group else current - group).map { it.name }.toSet()).apply()
    }

    @Synchronized fun mark(context: Context, originalTarget: PersonalCollectionTarget, group: PersonalCollectionGroup, obtained: Boolean) {
        val target = canonicalRegionalTarget(context, originalTarget)
        if (obtained && !supports(target, group)) return
        val prefs = preferences(context)
        val entries = readEntries(context, prefs)
        val old = entries[target.key]?.groups.orEmpty()
        val groups = if (obtained) old + group else old - group
        val existingKeys = prefs.getStringSet("entry_keys", null)
        val keys = (existingKeys ?: entries.keys).toMutableSet()
        val editor = prefs.edit()
        // Remove aliases of this target so unmarking cannot resurrect a legacy flag.
        keys.filter { it != target.key && canonicalRegionalTarget(context, PersonalCollectionTarget(it, "")).key == target.key }
            .forEach { keys.remove(it); editor.remove("entry:" + it) }
        // Migrate the legacy array atomically on the first edit.
        if (existingKeys == null) {
            entries.values.forEach { editor.putString("entry:" + it.target.key, encodeEntry(it)) }
            editor.remove("entries")
        }
        if (groups.isEmpty()) {
            keys.remove(target.key)
            editor.remove("entry:" + target.key)
        } else {
            keys.add(target.key)
            editor.putString("entry:" + target.key, encodeEntry(PersonalCollectionEntry(target, groups)))
        }
        if (existingKeys == null || keys != existingKeys) editor.putStringSet("entry_keys", keys)
        editor.apply()
    }
}