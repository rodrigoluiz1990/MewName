package com.mewname.app.domain

import org.json.JSONObject

internal data class PokedexEvolution(
    val from: String, val to: String, val candy: Int?, val purifiedCandy: Int?,
    val tradeFree: Boolean, val requirements: JSONObject,
    val megaEnergy: Int? = null, val spriteFile: String? = null, val gigantamax: Boolean = false
)
internal data class PokedexEvolutionPath(val members: List<String>, val steps: List<PokedexEvolution>)

internal fun parsePokedexEvolutions(json: String): List<PokedexEvolution> {
    val rows = JSONObject(json).getJSONArray("edges")
    return (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        PokedexEvolution(row.getString("from"), row.getString("to"),
            if (row.isNull("candy")) null else row.getInt("candy"),
            if (row.isNull("purified")) null else row.getInt("purified"),
            row.optBoolean("tradeFree"), row.optJSONObject("requirements") ?: JSONObject(),
            if (row.isNull("megaEnergy")) null else row.getInt("megaEnergy"),
            row.optString("spriteFile").takeIf { it.isNotBlank() })
    }
}

/** Follow actual edges, never the arbitrary order in families.json. */
internal fun pokedexEvolutionPaths(family: List<String>, evolutions: List<PokedexEvolution>): List<PokedexEvolutionPath> {
    val members = family.toMutableSet()
    // Include ancestors and every outgoing branch, including temporary Mega forms.
    do {
        val previousSize = members.size
        evolutions.forEach { if (it.from in members || it.to in members) { members += it.from; members += it.to } }
    } while (members.size != previousSize)
    val edges = evolutions.filter { it.from in members && it.to in members && it.from != it.to }
        .distinctBy { it.from to it.to }
    val outgoing = edges.groupBy { it.from }
    val targets = edges.map { it.to }.toSet()
    val paths = mutableListOf<PokedexEvolutionPath>()
    fun visit(names: List<String>, steps: List<PokedexEvolution>) {
        val next = outgoing[names.last()].orEmpty().filter { it.to !in names }
        if (next.isEmpty()) paths += PokedexEvolutionPath(names, steps)
        else next.forEach { visit(names + it.to, steps + it) }
    }
    members.filter { it !in targets }.forEach { visit(listOf(it), emptyList()) }
    val covered = paths.flatMap { it.members }.toSet()
    // Preserve isolated members and malformed cyclic data without inventing connections.
    members.filter { it !in covered }.forEach { paths += PokedexEvolutionPath(listOf(it), emptyList()) }
    return paths
}
/** Alternative forms from the same supported catalog and GO sprites used by Collections. */
internal fun pokedexGigantamaxForms(context: android.content.Context, images: PersonalCollectionImages): List<PokedexEvolution> {
    val names = org.json.JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
    return buildList {
        for (index in 0 until names.length()) {
            val row = names.getJSONObject(index)
            val name = row.getString("name")
            if (name.uppercase(java.util.Locale.ROOT) !in CollectionTransformations.gigantamax) continue
            val dex = row.getInt("dex")
            val sprite = listOf("GIGANTAMAX", "AMPED_GIGANTAMAX", "LOW_KEY_GIGANTAMAX")
                .map { "pm$dex.f$it.icon.png" }.firstOrNull { images.gameSprite(it) != null } ?: continue
            add(PokedexEvolution(name, "$name Gigamax", null, null, false, JSONObject(),
                spriteFile = sprite, gigantamax = true))
        }
    }
}
internal fun pokedexCandyColors(context: android.content.Context, paths: List<PokedexEvolutionPath>): Map<String, Pair<Int, Int>> {
    val rows = JSONObject(context.assets.open("pokedex_icons/candy_colors.json").bufferedReader().use { it.readText() }).getJSONArray("CandyColors")
    fun color(value: JSONObject) = android.graphics.Color.rgb(
        (value.getDouble("r") * 255).toInt(), (value.getDouble("g") * 255).toInt(), (value.getDouble("b") * 255).toInt())
    val palette = (0 until rows.length()).associate { i -> rows.getJSONObject(i).let {
        it.getInt("FamilyId") to (color(it.getJSONObject("PrimaryColor")) to color(it.getJSONObject("SecondaryColor")))
    } }
    val names = org.json.JSONArray(context.assets.open("pokemon/names.json").bufferedReader().use { it.readText() })
    val dex = (0 until names.length()).associate { i -> names.getJSONObject(i).let { it.getString("name") to it.getInt("dex") } }
    return buildMap {
        paths.forEach { path ->
            val colors = path.members.firstNotNullOfOrNull { name -> dex[name]?.let { palette[it] } }
            if (colors != null) path.members.forEach { put(it, colors) }
        }
    }
}
internal fun pokedexMegaColors(context: android.content.Context, edges: List<PokedexEvolution>): Map<String, Pair<Int, Int>> {
    val colors = JSONObject(context.assets.open("pokedex_icons/mega_colors.json").bufferedReader().use { it.readText() })
    fun color(value: JSONObject) = android.graphics.Color.rgb(
        (value.getDouble("r") * 255).toInt(), (value.getDouble("g") * 255).toInt(), (value.getDouble("b") * 255).toInt())
    return buildMap {
        edges.filter { it.megaEnergy != null }.forEach { edge ->
            val sprite = edge.spriteFile ?: return@forEach
            val dex = sprite.substringAfter("pm").substringBefore(".").padStart(4, '0')
            val form = sprite.substringAfter(".f").substringBefore(".icon")
            val palette = colors.optJSONObject("${dex}_$form") ?: colors.optJSONObject(dex) ?: return@forEach
            put(edge.to, color(palette.getJSONObject("_RampColor3")) to color(palette.getJSONObject("_RampColor4")))
        }
    }
}