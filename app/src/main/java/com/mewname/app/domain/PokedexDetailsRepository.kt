package com.mewname.app.domain

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale

internal data class PokedexLocalDetails(
    val types: List<String>, val stats: List<Int?>, val family: List<String>,
    val moves: PokemonMoveSet, val moveStats: Map<String, MoveCatalogEntry>,
    val matchups: List<TypeMatchupEntry>, val cp: Map<Int, Pair<Int?, Int?>>,
    val master: Map<Int, Triple<Int, Int, Int>>, val masterFamily: String,
    val sprite: String?, val shiny: String?
)
internal data class PokedexOnlineSnapshot(val json: String, val fetchedAt: Long, val stale: Boolean)
internal data class PokedexLeagueDetails(val cap: Int, val rank: Int?, val score: Double?,
    val moves: List<String>, val wins: List<String>, val counters: List<String>,
    val snapshot: PokedexOnlineSnapshot)

internal class PokedexDetailsRepository(private val context: Context) {
    fun local(entry: PokedexCatalogEntry, language: AppLanguage): PokedexLocalDetails {
        val raw = JSONObject(context.assets.open(AssetPaths.POKEMON_STATS).bufferedReader().use { it.readText() })
        val key = raw.keys().asSequence().firstOrNull { pokedexIdentity(it) == pokedexIdentity(entry.name) }
        val stats = key?.let { raw.getJSONObject(it) }
        val metadata = RaidCounterRepository(context).metadata()
        val raid = (listOf(entry.name) + entry.aliases).firstNotNullOfOrNull { metadata.resolve(it) }
        val types = raid?.types ?: listOfNotNull(entry.type1, entry.type2)
        val family = PokemonFamilySuggester().familyMembersFor(context, null, entry.name)
        val moves = PokemonMoveRepository.load(context, entry.name, language)
        val catalog = GameInfoRepository.loadMoveCatalog(context).associateBy { pokedexIdentity(it.name) }
        val calculator = PvpRankCalculator()
        val cp = listOf(15,20,25,40,50,51).associateWith { level ->
            if (stats == null) null to null else
                calculator.estimateCpAtLevel(context, entry.name, 10,10,10,level.toDouble()) to
                    calculator.estimateCpAtLevel(context, entry.name, 15,15,15,level.toDouble())
        }
        val master = MasterIvBadgeCatalog()
        val target = PersonalCollectionTarget(JSONArray(listOf(entry.name.uppercase(Locale.ROOT), "")).toString(), entry.name)
        val images = PersonalCollectionImages(context)
        return PokedexLocalDetails(types, listOf("attack","defense","stamina").map { stats?.optInt(it) }, family,
            moves, catalog, GameInfoRepository.matchupAgainst(context, types), cp,
            master.bestCombinations(context, family, entry.name), master.combinationFamilyName(family, entry.name),
            images.url(target), images.url(target, true))
    }

    /** Fresh data for 24h; a failed refresh never replaces a good file. Call on Dispatchers.IO. */
    fun online(fileName: String, force: Boolean = false): PokedexOnlineSnapshot {
        require(fileName in setOf("gamemaster.json", "rankings-1500.json", "rankings-2500.json", "rankings-10000.json"))
        val file = AtomicFile(File(context.filesDir, "pokedex-online-v1/$fileName"))
        val cached = runCatching { JSONObject(file.openRead().bufferedReader().use { it.readText() }) }.getOrNull()
        val timestamp = cached?.optLong("fetchedAt") ?: 0L
        if (!force && cached != null && System.currentTimeMillis() - timestamp < 86_400_000L)
            return PokedexOnlineSnapshot(cached.getString("data"), timestamp, false)
        val path = if (fileName == "gamemaster.json") fileName else "rankings/all/overall/$fileName"
        try {
            val connection = URL("https://raw.githubusercontent.com/pvpoke/pvpoke/master/src/data/$path").openConnection() as HttpURLConnection
            val data = try {
                connection.connectTimeout = 10_000; connection.readTimeout = 15_000
                require(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
                connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 8 * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                    String(output.toByteArray(), Charsets.UTF_8)
                }
            } finally { connection.disconnect() }
            if (fileName == "gamemaster.json") require(JSONObject(data).getJSONArray("pokemon").length() > 0)
            else require(JSONArray(data).length() > 0)
            val now = System.currentTimeMillis()
            synchronized(cacheLock) {
                file.baseFile.parentFile?.mkdirs()
                val stream = file.startWrite()
                try { stream.write(JSONObject().put("fetchedAt", now).put("data", data).toString().toByteArray()); file.finishWrite(stream) }
                catch (error: Exception) { file.failWrite(stream); throw error }
            }
            return PokedexOnlineSnapshot(data, now, false)
        } catch (error: Exception) {
            if (cached != null) return PokedexOnlineSnapshot(cached.getString("data"), timestamp, true)
            throw error
        }
    }
    companion object { private val cacheLock = Any() }
}

internal fun pokedexIdentity(text: String): String = Normalizer.normalize(text.replace("♀", "female").replace("♂", "male"), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    .replace(Regex("\\bhisui\\b"), "hisuian").replace(Regex("\\bgalar\\b"), "galarian")
    .replace(Regex("\\balola\\b"), "alolan").replace(Regex("\\bpaldea\\b"), "paldean")
    .replace(Regex("[^a-z0-9]"), "")

internal fun matchPvpokeSpecies(pokemon: JSONArray, entry: PokedexCatalogEntry): JSONObject? {
    val keys = (entry.aliases + entry.name).map(::pokedexIdentity).toSet()
    val candidates = (0 until pokemon.length()).map { pokemon.getJSONObject(it) }
        .filter { it.optInt("dex") == entry.number &&
            (pokedexIdentity(it.optString("speciesId")) in keys || pokedexIdentity(it.optString("speciesName")) in keys) }
    return candidates.singleOrNull()
}

internal fun parsePokedexLeague(cap: Int, speciesId: String, snapshot: PokedexOnlineSnapshot): PokedexLeagueDetails {
    val array = JSONArray(snapshot.json)
    val index = (0 until array.length()).firstOrNull { array.getJSONObject(it).optString("speciesId") == speciesId }
    val row = index?.let { array.getJSONObject(it) }
    fun opponents(key: String): List<String> = row?.optJSONArray(key)?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it).getString("opponent") }
    }.orEmpty()
    return PokedexLeagueDetails(cap, index?.plus(1), row?.optDouble("score")?.takeIf { it.isFinite() },
        raidStrings(row?.optJSONArray("moveset")), opponents("matchups"), opponents("counters"), snapshot)
}
