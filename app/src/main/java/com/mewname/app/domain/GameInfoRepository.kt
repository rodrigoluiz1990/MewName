package com.mewname.app.domain

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class MoveCategory {
    FAST,
    CHARGED
}

data class MoveCatalogEntry(
    val name: String,
    val namePt: String? = null,
    val nameEs: String? = null,
    val type: String,
    val power: Int? = null,
    val energyDelta: Int? = null,
    val duration: Int? = null,
    val pvpPower: Int? = null,
    val pvpEnergyDelta: Int? = null,
    val pvpTurnDuration: Int? = null,
    val moveId: Int? = null,
    val category: MoveCategory
) {
    fun localizedName(language: AppLanguage): String {
        return when (language) {
            AppLanguage.PT_BR -> namePt ?: name
            AppLanguage.ES -> nameEs ?: name
            AppLanguage.EN -> name
        }
    }
}

data class PokedexCatalogEntry(
    val number: Int,
    val name: String,
    val namePt: String? = null,
    val nameEs: String? = null,
    val type1: String? = null,
    val type2: String? = null,
    val attack: Int? = null,
    val defense: Int? = null,
    val stamina: Int? = null,
    val forms: List<String> = emptyList(),
    val aliases: List<String> = emptyList()
) {
    fun localizedName(language: AppLanguage): String {
        return when (language) {
            AppLanguage.PT_BR -> namePt ?: name
            AppLanguage.ES -> nameEs ?: name
            AppLanguage.EN -> name
        }
    }
}

data class BattleSuggestionEntry(
    val name: String,
    val attackTypes: List<String>,
    val labels: List<String> = emptyList(),
    val searchTerms: List<String>,
    val requiredSearchTag: String? = null
)

data class TypeMatchupEntry(
    val attackType: String,
    val multiplier: Double
)

data class BattlePokemonIndexEntry(
    val name: String,
    val normalizedNames: List<String>,
    val types: List<String>
)

data class RaidHistoryCatalog(
    val updatedAt: String,
    val sourceUrl: String,
    val categories: List<RaidHistoryCategory>
)

data class RaidHistoryCategory(
    val id: String,
    val titlePt: String,
    val titleEn: String,
    val titleEs: String,
    val items: List<RaidHistoryItem>
) {
    fun localizedTitle(language: AppLanguage): String {
        return when (language) {
            AppLanguage.PT_BR -> titlePt
            AppLanguage.ES -> titleEs
            AppLanguage.EN -> titleEn
        }
    }
}

data class RaidHistoryItem(
    val name: String,
    val url: String,
    val lastStart: String,
    val lastEnd: String
)

private data class PvpMoveStats(
    val power: Int?,
    val energyDelta: Int?,
    val turnDuration: Int?
)

object GameInfoRepository {
    @Volatile
    private var moveCatalogCache: List<MoveCatalogEntry>? = null

    @Volatile
    private var pokedexCache: List<PokedexCatalogEntry>? = null

    @Volatile
    private var typeEffectivenessCache: Map<String, Map<String, Double>>? = null

    @Volatile
    private var battlePokemonIndexCache: List<BattlePokemonIndexEntry>? = null

    @Volatile
    private var raidHistoryCache: RaidHistoryCatalog? = null

    @Volatile
    private var pokemonDexOrderCache: Map<String, Int>? = null

    private val localizedPokemonSearchTermCache = mutableMapOf<AppLanguage, Map<String, String>>()

    fun loadMoveCatalog(context: Context): List<MoveCatalogEntry> {
        moveCatalogCache?.let { return it }
        synchronized(this) {
            moveCatalogCache?.let { return it }
            val loaded = loadMoves(context, AssetPaths.FAST_MOVES, MoveCategory.FAST) +
                loadMoves(context, AssetPaths.CHARGED_MOVES, MoveCategory.CHARGED)
            moveCatalogCache = loaded
            return loaded
        }
    }

    suspend fun loadMoveCatalogProgressive(
        context: Context,
        onUpdate: suspend (List<MoveCatalogEntry>) -> Unit
    ): List<MoveCatalogEntry> {
        moveCatalogCache?.let {
            onUpdate(it)
            return it
        }

        val loadedProgress = mutableListOf<MoveCatalogEntry>()
        loadedProgress += loadMovesRawProgressive(context, AssetPaths.FAST_MOVES, MoveCategory.FAST) { partial ->
            onUpdate(loadedProgress + partial)
        }
        onUpdate(loadedProgress.toList())

        val chargedProgress = mutableListOf<MoveCatalogEntry>()
        val chargedMoves = loadMovesRawProgressive(context, AssetPaths.CHARGED_MOVES, MoveCategory.CHARGED) { partial ->
            chargedProgress.clear()
            chargedProgress += partial
            onUpdate(loadedProgress + chargedProgress)
        }

        val rawLoaded = loadedProgress + chargedMoves
        onUpdate(rawLoaded)

        val moveNamesPt = GameTextRepository.moveTranslations(context, AppLanguage.PT_BR)
        val moveNamesEs = GameTextRepository.moveTranslations(context, AppLanguage.ES)
        val pvpFastStats = loadPvpMoveStats(context, AssetPaths.PVP_FAST_MOVES)
        val pvpChargedStats = loadPvpMoveStats(context, AssetPaths.PVP_CHARGED_MOVES)
        fun enrich(entry: MoveCatalogEntry): MoveCatalogEntry {
            val pvpStats = entry.moveId?.let {
                if (entry.category == MoveCategory.FAST) pvpFastStats[it] else pvpChargedStats[it]
            }
            return entry.copy(
                namePt = entry.moveId?.let { moveNamesPt[it] },
                nameEs = entry.moveId?.let { moveNamesEs[it] },
                pvpPower = pvpStats?.power,
                pvpEnergyDelta = pvpStats?.energyDelta,
                pvpTurnDuration = pvpStats?.turnDuration
            )
        }

        val loaded = rawLoaded.map(::enrich)
            .sortedWith(compareBy<MoveCatalogEntry> { it.type }.thenBy { it.name })
        moveCatalogCache = loaded
        onUpdate(loaded)
        return loaded
    }

    fun loadPokedexCatalog(context: Context): List<PokedexCatalogEntry> {
        pokedexCache?.let { return it }
        synchronized(this) {
            pokedexCache?.let { return it }

            val namesArray = JSONArray(
                context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
            )
            val pokemonTypesArray = JSONArray(
                context.assets.open(AssetPaths.POKEMON_TYPES_CATALOG).bufferedReader().use { it.readText() }
            )
            val statsObject = JSONObject(
                context.assets.open(AssetPaths.POKEMON_STATS).bufferedReader().use { it.readText() }
            )

            val aliasMap = buildMap<String, List<String>> {
                for (index in 0 until namesArray.length()) {
                    val item = namesArray.getJSONObject(index)
                    put(
                        normalizeCatalogName(item.getString("name")),
                        item.optJSONArrayStrings("aliases")
                    )
                }
            }
            val pokemonNamesPt = GameTextRepository.pokemonTranslations(context, AppLanguage.PT_BR)
            val pokemonNamesEs = GameTextRepository.pokemonTranslations(context, AppLanguage.ES)

            val formMap = linkedMapOf<String, MutableList<FormTypeInfo>>()
            for (index in 0 until pokemonTypesArray.length()) {
                val item = pokemonTypesArray.getJSONObject(index)
                val pokemonName = item.getString("pokemon_name")
                formMap.getOrPut(normalizeCatalogName(pokemonName)) { mutableListOf() }
                    .add(
                        FormTypeInfo(
                            form = item.optString("form").ifBlank { "Normal" },
                            types = item.optJSONArrayStrings("type")
                        )
                    )
            }

            val loaded = buildList {
                for (index in 0 until namesArray.length()) {
                    val item = namesArray.getJSONObject(index)
                    val name = item.getString("name")
                    val normalized = normalizeCatalogName(name)
                    val forms = formMap[normalized].orEmpty()
                    val preferred = forms.firstOrNull { it.form.equals("Normal", ignoreCase = true) } ?: forms.firstOrNull()
                    val stats = statsObject.optJSONObject(normalized)
                    add(
                        PokedexCatalogEntry(
                            number = item.optInt("dex"),
                            name = name,
                            namePt = pokemonNamesPt[item.optInt("dex")],
                            nameEs = pokemonNamesEs[item.optInt("dex")],
                            type1 = preferred?.types?.getOrNull(0),
                            type2 = preferred?.types?.getOrNull(1),
                            attack = stats?.optInt("attack"),
                            defense = stats?.optInt("defense"),
                            stamina = stats?.optInt("stamina"),
                            forms = forms.map { it.form }.distinct(),
                            aliases = aliasMap[normalized].orEmpty()
                        )
                    )
                }
            }.sortedBy { it.number }

            pokedexCache = loaded
            return loaded
        }
    }

    suspend fun loadPokedexCatalogProgressive(
        context: Context,
        batchSize: Int = 96,
        onUpdate: suspend (List<PokedexCatalogEntry>) -> Unit
    ): List<PokedexCatalogEntry> {
        pokedexCache?.let {
            onUpdate(it)
            return it
        }

        val namesArray = JSONArray(
            context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
        )

        val basicLoaded = mutableListOf<PokedexCatalogEntry>()
        for (index in 0 until namesArray.length()) {
            val item = namesArray.getJSONObject(index)
            basicLoaded += PokedexCatalogEntry(
                number = item.optInt("dex"),
                name = item.getString("name"),
                aliases = item.optJSONArrayStrings("aliases")
            )
            if (basicLoaded.size % batchSize == 0) {
                onUpdate(basicLoaded.toList())
            }
        }
        if (basicLoaded.isNotEmpty()) {
            onUpdate(basicLoaded.toList())
        }

        val pokemonTypesArray = JSONArray(
            context.assets.open(AssetPaths.POKEMON_TYPES_CATALOG).bufferedReader().use { it.readText() }
        )
        val statsObject = JSONObject(
            context.assets.open(AssetPaths.POKEMON_STATS).bufferedReader().use { it.readText() }
        )

        val aliasMap = buildMap<String, List<String>> {
            for (index in 0 until namesArray.length()) {
                val item = namesArray.getJSONObject(index)
                put(
                    normalizeCatalogName(item.getString("name")),
                    item.optJSONArrayStrings("aliases")
                )
            }
        }
        val pokemonNamesPt = GameTextRepository.pokemonTranslations(context, AppLanguage.PT_BR)
        val pokemonNamesEs = GameTextRepository.pokemonTranslations(context, AppLanguage.ES)

        val formMap = linkedMapOf<String, MutableList<FormTypeInfo>>()
        for (index in 0 until pokemonTypesArray.length()) {
            val item = pokemonTypesArray.getJSONObject(index)
            val pokemonName = item.getString("pokemon_name")
            formMap.getOrPut(normalizeCatalogName(pokemonName)) { mutableListOf() }
                .add(
                    FormTypeInfo(
                        form = item.optString("form").ifBlank { "Normal" },
                        types = item.optJSONArrayStrings("type")
                    )
                )
        }

        val loaded = mutableListOf<PokedexCatalogEntry>()
        for (index in 0 until namesArray.length()) {
            val item = namesArray.getJSONObject(index)
            val name = item.getString("name")
            val normalized = normalizeCatalogName(name)
            val forms = formMap[normalized].orEmpty()
            val preferred = forms.firstOrNull { it.form.equals("Normal", ignoreCase = true) } ?: forms.firstOrNull()
            val stats = statsObject.optJSONObject(normalized)
            loaded += PokedexCatalogEntry(
                number = item.optInt("dex"),
                name = name,
                namePt = pokemonNamesPt[item.optInt("dex")],
                nameEs = pokemonNamesEs[item.optInt("dex")],
                type1 = preferred?.types?.getOrNull(0),
                type2 = preferred?.types?.getOrNull(1),
                attack = stats?.optInt("attack"),
                defense = stats?.optInt("defense"),
                stamina = stats?.optInt("stamina"),
                forms = forms.map { it.form }.distinct(),
                aliases = aliasMap[normalized].orEmpty()
            )

            if (loaded.size % batchSize == 0) {
                onUpdate(loaded.toList())
            }
        }

        val finalList = loaded.sortedBy { it.number }
        pokedexCache = finalList
        onUpdate(finalList)
        return finalList
    }

    fun loadTypeEffectiveness(context: Context): Map<String, Map<String, Double>> {
        typeEffectivenessCache?.let { return it }
        synchronized(this) {
            typeEffectivenessCache?.let { return it }
            val root = JSONObject(
                context.assets.open(AssetPaths.TYPE_EFFECTIVENESS).bufferedReader().use { it.readText() }
            )
            val loaded = buildMap {
                root.keys().forEach { attackType ->
                    val defenders = root.getJSONObject(attackType)
                    put(
                        attackType,
                        buildMap {
                            defenders.keys().forEach { defenseType ->
                                put(defenseType, defenders.optDouble(defenseType, 1.0))
                            }
                        }
                    )
                }
            }
            typeEffectivenessCache = loaded
            return loaded
        }
    }

    fun loadRaidHistory(context: Context): RaidHistoryCatalog {
        raidHistoryCache?.let { return it }
        synchronized(this) {
            raidHistoryCache?.let { return it }
            val root = JSONObject(
                context.assets.open(AssetPaths.RAID_HISTORY).bufferedReader().use { it.readText() }
            )
            val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
            val categories = buildList {
                for (categoryIndex in 0 until categoriesArray.length()) {
                    val category = categoriesArray.getJSONObject(categoryIndex)
                    val itemsArray = category.optJSONArray("items") ?: JSONArray()
                    val items = buildList {
                        for (itemIndex in 0 until itemsArray.length()) {
                            val item = itemsArray.getJSONObject(itemIndex)
                            add(
                                RaidHistoryItem(
                                    name = item.optString("name"),
                                    url = item.optString("url"),
                                    lastStart = item.optString("lastStart"),
                                    lastEnd = item.optString("lastEnd")
                                )
                            )
                        }
                    }
                    add(
                        RaidHistoryCategory(
                            id = category.optString("id"),
                            titlePt = category.optString("titlePt"),
                            titleEn = category.optString("titleEn"),
                            titleEs = category.optString("titleEs"),
                            items = items
                        )
                    )
                }
            }
            val loaded = RaidHistoryCatalog(
                updatedAt = root.optString("updatedAt"),
                sourceUrl = root.optString("sourceUrl"),
                categories = categories
            )
            raidHistoryCache = loaded
            return loaded
        }
    }

    fun loadPokemonDexOrder(context: Context): Map<String, Int> {
        pokemonDexOrderCache?.let { return it }
        synchronized(this) {
            pokemonDexOrderCache?.let { return it }
            val namesArray = JSONArray(
                context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
            )
            val loaded = buildMap {
                for (index in 0 until namesArray.length()) {
                    val item = namesArray.getJSONObject(index)
                    val dex = item.optInt("dex")
                    if (dex <= 0) continue
                    (listOf(item.optString("name")) + item.optJSONArrayStrings("aliases"))
                        .map(::normalizeBattleText)
                        .filter { it.isNotBlank() }
                        .forEach { normalizedName -> put(normalizedName, dex) }
                }
            }
            pokemonDexOrderCache = loaded
            return loaded
        }
    }

    fun loadBattlePokemonIndex(context: Context): List<BattlePokemonIndexEntry> {
        battlePokemonIndexCache?.let { return it }
        synchronized(this) {
            battlePokemonIndexCache?.let { return it }

            val namesArray = JSONArray(
                context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
            )
            val pokemonTypesArray = JSONArray(
                context.assets.open(AssetPaths.POKEMON_TYPES_CATALOG).bufferedReader().use { it.readText() }
            )
            val formMap = linkedMapOf<String, MutableList<FormTypeInfo>>()
            for (index in 0 until pokemonTypesArray.length()) {
                val item = pokemonTypesArray.getJSONObject(index)
                val pokemonName = item.getString("pokemon_name")
                formMap.getOrPut(normalizeCatalogName(pokemonName)) { mutableListOf() }
                    .add(
                        FormTypeInfo(
                            form = item.optString("form").ifBlank { "Normal" },
                            types = item.optJSONArrayStrings("type")
                        )
                    )
            }

            val loaded = buildList {
                for (index in 0 until namesArray.length()) {
                    val item = namesArray.getJSONObject(index)
                    val name = item.getString("name")
                    val forms = formMap[normalizeCatalogName(name)].orEmpty()
                    val preferred = forms.firstOrNull { it.form.equals("Normal", ignoreCase = true) } ?: forms.firstOrNull()
                    val aliases = item.optJSONArrayStrings("aliases")
                    val normalizedNames = (listOf(name) + aliases)
                        .map(::normalizeBattleText)
                        .filter { it.length >= 3 }
                        .distinct()
                    add(
                        BattlePokemonIndexEntry(
                            name = name,
                            normalizedNames = normalizedNames,
                            types = preferred?.types.orEmpty()
                        )
                    )
                }
            }

            battlePokemonIndexCache = loaded
            return loaded
        }
    }

    fun localizedPokemonSearchTerm(
        context: Context,
        term: String,
        language: AppLanguage
    ): String? {
        val normalized = normalizeBattleText(term)
        if (normalized.isBlank()) return null
        return loadLocalizedPokemonSearchTerms(context, language)[normalized]
    }

    private fun loadLocalizedPokemonSearchTerms(
        context: Context,
        language: AppLanguage
    ): Map<String, String> {
        localizedPokemonSearchTermCache[language]?.let { return it }
        synchronized(this) {
            localizedPokemonSearchTermCache[language]?.let { return it }

            val namesArray = JSONArray(
                context.assets.open(AssetPaths.POKEMON_NAMES).bufferedReader().use { it.readText() }
            )
            val translatedNames = if (language == AppLanguage.EN) {
                emptyMap()
            } else {
                GameTextRepository.pokemonTranslations(context, language)
            }
            val loaded = buildMap {
                for (index in 0 until namesArray.length()) {
                    val item = namesArray.getJSONObject(index)
                    val dex = item.optInt("dex")
                    val name = item.getString("name")
                    val localized = translatedNames[dex] ?: name
                    val output = localized.lowercase(Locale.US)
                    (listOf(name, localized) + item.optJSONArrayStrings("aliases"))
                        .map(::normalizeBattleText)
                        .filter { it.length >= 2 }
                        .distinct()
                        .forEach { key -> putIfAbsent(key, output) }
                }
            }
            localizedPokemonSearchTermCache[language] = loaded
            return loaded
        }
    }

    fun loadRaidMetaSnapshot(): List<BattleSuggestionEntry> = listOf(
        battleEntry("Mega Rayquaza", listOf("Dragon", "Flying"), "rayquaza", "mega"),
        battleEntry("Mega Lucario", listOf("Fighting", "Steel"), "lucario", "mega"),
        battleEntry("Primal Groudon", listOf("Ground", "Fire"), "groudon", "primal"),
        battleEntry("White Kyurem", listOf("Ice", "Dragon"), "kyurem"),
        battleEntry("Mega Blaziken", listOf("Fire", "Fighting"), "blaziken", "mega"),
        battleEntry("Necrozma Dawn Wings", listOf("Ghost", "Psychic"), "necrozma"),
        battleEntry("Black Kyurem", listOf("Dragon", "Ice"), "kyurem"),
        battleEntry("Kartana", listOf("Grass", "Steel"), "kartana"),
        battleEntry("Xurkitree", listOf("Electric"), "xurkitree"),
        battleEntry("Primal Kyogre", listOf("Water"), "kyogre", "primal"),
        battleEntry("Mega Tyranitar", listOf("Dark", "Rock"), "tyranitar", "mega"),
        battleEntry("Mega Diancie", listOf("Rock", "Fairy"), "diancie", "mega"),
        battleEntry("Mega Garchomp", listOf("Ground", "Dragon"), "garchomp", "mega"),
        battleEntry("Shadow Mewtwo", listOf("Psychic"), "mewtwo", "shadow"),
        battleEntry("Mega Charizard Y", listOf("Fire", "Flying"), "charizard", "mega"),
        battleEntry("Mega Gengar", listOf("Ghost", "Poison"), "gengar", "mega"),
        battleEntry("Mega Sceptile", listOf("Grass", "Dragon"), "sceptile", "mega"),
        battleEntry("Keldeo", listOf("Fighting", "Water"), "keldeo"),
        battleEntry("Eternatus", listOf("Dragon"), "eternatus"),
        battleEntry("Zamazenta Crowned", listOf("Steel"), "zamazenta")
    )

    fun loadMaxBattleRoster(): List<BattleSuggestionEntry> = listOf(
        maxBattleEntry("Gigantamax Machamp", listOf("Fighting"), "gmax", "machamp"),
        maxBattleEntry("Gigantamax Charizard", listOf("Fire", "Flying"), "gmax", "charizard"),
        maxBattleEntry("Gigantamax Venusaur", listOf("Grass", "Poison"), "gmax", "venusaur"),
        maxBattleEntry("Gigantamax Blastoise", listOf("Water"), "gmax", "blastoise"),
        maxBattleEntry("Gigantamax Gengar", listOf("Ghost", "Poison"), "gmax", "gengar"),
        maxBattleEntry("Gigantamax Lapras", listOf("Water", "Ice"), "gmax", "lapras"),
        maxBattleEntry("Gigantamax Kingler", listOf("Water"), "gmax", "kingler"),
        maxBattleEntry("Gigantamax Toxtricity", listOf("Electric", "Poison"), "gmax", "toxtricity"),
        maxBattleEntry("Gigantamax Snorlax", listOf("Normal"), "gmax", "snorlax"),
        maxBattleEntry("Gigantamax Butterfree", listOf("Bug", "Flying"), "gmax", "butterfree"),
        maxBattleEntry("Gigantamax Pikachu", listOf("Electric"), "gmax", "pikachu"),
        maxBattleEntry("Gigantamax Meowth", listOf("Normal"), "gmax", "meowth"),
        maxBattleEntry("Gigantamax Rillaboom", listOf("Grass"), "gmax", "rillaboom"),
        maxBattleEntry("Gigantamax Cinderace", listOf("Fire"), "gmax", "cinderace"),
        maxBattleEntry("Gigantamax Inteleon", listOf("Water"), "gmax", "inteleon"),
        maxBattleEntry("Gigantamax Grimmsnarl", listOf("Dark", "Fairy"), "gmax", "grimmsnarl"),
        maxBattleEntry("Zacian Crowned Sword", listOf("Fairy", "Steel"), "dmax", "zacian"),
        maxBattleEntry("Zamazenta Crowned Shield", listOf("Fighting", "Steel"), "dmax", "zamazenta"),
        maxBattleEntry("Dynamax Metagross", listOf("Steel", "Psychic"), "dmax", "metagross"),
        maxBattleEntry("Dynamax Excadrill", listOf("Ground", "Steel"), "dmax", "excadrill"),
        maxBattleEntry("Dynamax Gengar", listOf("Ghost", "Poison"), "dmax", "gengar"),
        maxBattleEntry("Dynamax Charizard", listOf("Fire", "Flying"), "dmax", "charizard"),
        maxBattleEntry("Dynamax Venusaur", listOf("Grass", "Poison"), "dmax", "venusaur"),
        maxBattleEntry("Dynamax Blastoise", listOf("Water"), "dmax", "blastoise"),
        maxBattleEntry("Dynamax Machamp", listOf("Fighting"), "dmax", "machamp"),
        maxBattleEntry("Dynamax Greedent", listOf("Normal"), "dmax", "greedent"),
        maxBattleEntry("Dynamax Dubwool", listOf("Normal"), "dmax", "dubwool"),
        maxBattleEntry("Dynamax Cinderace", listOf("Fire"), "dmax", "cinderace"),
        maxBattleEntry("Dynamax Inteleon", listOf("Water"), "dmax", "inteleon"),
        maxBattleEntry("Dynamax Rillaboom", listOf("Grass"), "dmax", "rillaboom"),
        maxBattleEntry("Dynamax Toxtricity", listOf("Electric", "Poison"), "dmax", "toxtricity"),
        maxBattleEntry("Dynamax Lapras", listOf("Water", "Ice"), "dmax", "lapras"),
        maxBattleEntry("Dynamax Kingler", listOf("Water"), "dmax", "kingler"),
        maxBattleEntry("Dynamax Snorlax", listOf("Normal"), "dmax", "snorlax"),
        maxBattleEntry("Dynamax Butterfree", listOf("Bug", "Flying"), "dmax", "butterfree")
    )

    fun matchupAgainst(
        context: Context,
        defenderTypes: List<String>
    ): List<TypeMatchupEntry> {
        if (defenderTypes.isEmpty()) return emptyList()
        val chart = loadTypeEffectiveness(context)
        return chart.keys.map { attackType ->
            val multiplier = defenderTypes.fold(1.0) { acc, defenderType ->
                acc * chart[attackType].orEmpty().getOrDefault(defenderType, 1.0)
            }
            TypeMatchupEntry(attackType = attackType, multiplier = multiplier)
        }.sortedWith(
            compareByDescending<TypeMatchupEntry> { it.multiplier }
                .thenBy { abs(it.multiplier - 1.0) }
                .thenBy { it.attackType }
        )
    }

    private fun loadMoves(
        context: Context,
        assetPath: String,
        category: MoveCategory
    ): List<MoveCatalogEntry> {
        val moveNamesPt = GameTextRepository.moveTranslations(context, AppLanguage.PT_BR)
        val moveNamesEs = GameTextRepository.moveTranslations(context, AppLanguage.ES)
        val pvpStats = loadPvpMoveStats(
            context,
            if (category == MoveCategory.FAST) AssetPaths.PVP_FAST_MOVES else AssetPaths.PVP_CHARGED_MOVES
        )
        val array = JSONArray(
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        )
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val moveId = item.optIntOrNull("move_id")
                val pvpMoveStats = moveId?.let { pvpStats[it] }
                add(
                    MoveCatalogEntry(
                        name = item.getString("name"),
                        namePt = moveId?.let { moveNamesPt[it] },
                        nameEs = moveId?.let { moveNamesEs[it] },
                        type = item.getString("type"),
                        power = item.optIntOrNull("power"),
                        energyDelta = item.optIntOrNull("energy_delta"),
                        duration = item.optIntOrNull("duration"),
                        pvpPower = pvpMoveStats?.power,
                        pvpEnergyDelta = pvpMoveStats?.energyDelta,
                        pvpTurnDuration = pvpMoveStats?.turnDuration,
                        moveId = moveId,
                        category = category
                    )
                )
            }
        }.sortedWith(compareBy<MoveCatalogEntry> { it.type }.thenBy { it.name })
    }

    private suspend fun loadMovesRawProgressive(
        context: Context,
        assetPath: String,
        category: MoveCategory,
        onUpdate: suspend (List<MoveCatalogEntry>) -> Unit
    ): List<MoveCatalogEntry> {
        val array = JSONArray(
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        )
        val loaded = mutableListOf<MoveCatalogEntry>()
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            loaded += MoveCatalogEntry(
                name = item.getString("name"),
                type = item.getString("type"),
                power = item.optIntOrNull("power"),
                energyDelta = item.optIntOrNull("energy_delta"),
                duration = item.optIntOrNull("duration"),
                moveId = item.optIntOrNull("move_id"),
                category = category
            )
            onUpdate(loaded.toList())
        }
        return loaded
    }

    private fun loadPvpMoveStats(context: Context, assetPath: String): Map<Int, PvpMoveStats> {
        val array = JSONArray(
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        )
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val moveId = item.optIntOrNull("move_id") ?: continue
                put(
                    moveId,
                    PvpMoveStats(
                        power = item.optIntOrNull("power"),
                        energyDelta = item.optIntOrNull("energy_delta"),
                        turnDuration = item.optIntOrNull("turn_duration")
                    )
                )
            }
        }
    }

    private fun battleEntry(
        name: String,
        attackTypes: List<String>,
        vararg searchTerms: String
    ) = BattleSuggestionEntry(
        name = name,
        attackTypes = attackTypes,
        labels = attackTypes,
        searchTerms = searchTerms.toList()
    )

    private fun maxBattleEntry(
        name: String,
        attackTypes: List<String>,
        requiredSearchTag: String,
        vararg searchTerms: String
    ) = BattleSuggestionEntry(
        name = name,
        attackTypes = attackTypes,
        labels = attackTypes,
        searchTerms = searchTerms.toList(),
        requiredSearchTag = requiredSearchTag
    )

    private fun normalizeCatalogName(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .trim()
    }

    private fun normalizeBattleText(text: String): String {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key)) return null
        return when (val raw = opt(key)) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optJSONArrayStrings(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private data class FormTypeInfo(
        val form: String,
        val types: List<String>
    )
}

