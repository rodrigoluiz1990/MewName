package com.mewname.app.domain

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor
import kotlin.math.sqrt

internal data class RaidMove(val id: String, val type: String, val special: Boolean = false)
internal data class RaidPokemon(val id: String, val types: List<String>, val attack: Int, val defense: Int,
    val stamina: Int, val fast: List<String>, val charged: List<String>, val elite: List<String>, val dex: Int = 0)
internal data class RaidCounter(val id: String, val fast: RaidMove, val charged: RaidMove, val estimator: Double)
internal data class RaidReport(val bossId: String, val tier: String, val level: Int, val fetchedAt: Long,
    val fast: List<String>, val charged: List<String>, val counters: List<RaidCounter>)
internal data class RaidChoice(val id: String, val tier: String)
internal fun raidTier(id: String, catalog: List<RaidChoice>): String = catalog.filter { it.id == id }.map { it.tier }.distinct().singleOrNull()
    ?: when { id.contains("_SHADOW") -> "RAID_LEVEL_5_SHADOW"; id.contains("_MEGA") || id.endsWith("_PRIMAL") -> "RAID_LEVEL_MEGA"; else -> "RAID_LEVEL_5" }
internal data class RaidMetadata(val pokemon: Map<String, RaidPokemon>, val moves: Map<String, String>) {
    fun resolve(name: String): RaidPokemon? {
        pokemon[name]?.let { return it }
        val key = raidKey(name)
        val matches = pokemon.values.filter { raidKey(it.id) == key || raidKey(it.id).split(' ').sorted() == key.split(' ').sorted() }
        return matches.singleOrNull() ?: when (key) {
            "zacian coroado" -> pokemon["ZACIAN_CROWNED_SWORD_FORM"]
            "zamazenta coroado" -> pokemon["ZAMAZENTA_CROWNED_SHIELD_FORM"]
            "zacian" -> pokemon["ZACIAN_HERO_FORM"]
            "zamazenta" -> pokemon["ZAMAZENTA_HERO_FORM"]
            else -> null
        }
    }
    fun move(id: String, owner: RaidPokemon? = null) = RaidMove(id, moves[id].orEmpty(), id in owner?.elite.orEmpty())
}
internal fun raidKey(value: String) = catalogText(value.replace("_FORM", "").replace("_FAST", ""))
internal fun raidName(id: String) = id.removeSuffix("_FORM").split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }
internal fun raidStrings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() && s != "null" } }
internal fun raidCp(p: RaidPokemon, iv: Int, boosted: Boolean): Int {
    val cpm = if (boosted) 0.667934000492096 else 0.597400009632110
    return floor((p.attack + iv) * sqrt((p.defense + iv).toDouble()) * sqrt((p.stamina + iv).toDouble()) * cpm * cpm / 10).toInt().coerceAtLeast(10)
}
internal fun raidWeather(types: List<String>): List<String> = types.mapNotNull { when(it) {
    "Grass", "Ground", "Fire" -> "sunny"
    "Water", "Electric", "Bug" -> "rain"
    "Normal", "Rock" -> "partly"
    "Fairy", "Fighting", "Poison" -> "cloudy"
    "Flying", "Dragon", "Psychic" -> "wind"
    "Steel", "Ice" -> "snow"
    "Dark", "Ghost" -> "fog"
    else -> null
} }.distinct()
internal fun raidCaptureId(id: String): String? = when {
    id.contains("_MEGA") -> id.substringBefore("_MEGA")
    id.endsWith("_PRIMAL") -> id.removeSuffix("_PRIMAL")
    id == "ZACIAN_CROWNED_SWORD_FORM" -> "ZACIAN_HERO_FORM"
    id == "ZAMAZENTA_CROWNED_SHIELD_FORM" -> "ZAMAZENTA_HERO_FORM"
    id == "NECROZMA_DUSK_MANE_FORM" || id == "NECROZMA_DAWN_WINGS_FORM" -> "NECROZMA"
    id.endsWith("_SHADOW_FORM") -> id.removeSuffix("_SHADOW_FORM")
    else -> id
}

internal object RaidJson {
    fun metadata(json: JSONObject): RaidMetadata {
        val moves = json.getJSONArray("moves")
        val mons = json.getJSONArray("pokemon")
        fun type(v: String) = v.removePrefix("POKEMON_TYPE_").lowercase().replaceFirstChar(Char::titlecase)
        return RaidMetadata((0 until mons.length()).associate { i ->
            val p = mons.getJSONObject(i); val stats = p.getJSONObject("stats"); val id = p.getString("id")
            id to RaidPokemon(id, raidStrings(p.optJSONArray("types")).map(::type), stats.getInt("baseAttack"),
                stats.getInt("baseDefense"), stats.getInt("baseStamina"), raidStrings(p.optJSONArray("fast")),
                raidStrings(p.optJSONArray("charged")).filterNot { it in setOf("RETURN", "FRUSTRATION") }, raidStrings(p.optJSONArray("elite")), p.optInt("dex"))
        }, (0 until moves.length()).associate { i -> moves.getJSONObject(i).let { it.getString("id") to type(it.getString("type")) } })
    }
    fun report(raw: JSONObject, id: String, tier: String, level: Int, meta: RaidMetadata): RaidReport {
        val boss = raw.getJSONArray("attackers").getJSONObject(0)
        require(boss.getString("pokemonId") == id && boss.getString("boss") == tier)
        val variants = boss.getJSONArray("byMove")
        val counters = boss.getJSONObject("randomMove").getJSONArray("defenders")
        val rows = (0 until counters.length()).mapNotNull { i ->
            val c = counters.getJSONObject(i); val cid = c.getString("pokemonId")
            val choices = c.getJSONArray("byMove")
            val best = (0 until choices.length()).map { choices.getJSONObject(it) }.filter {
                val v = it.optJSONObject("result")?.optDouble("estimator", Double.NaN) ?: Double.NaN
                v.isFinite() && v > 0
            }.minByOrNull { it.getJSONObject("result").getDouble("estimator") } ?: return@mapNotNull null
            if (cid !in meta.pokemon) return@mapNotNull null
            RaidCounter(cid, meta.move(best.getString("move1"), meta.pokemon[cid]), meta.move(best.getString("move2"), meta.pokemon[cid]),
                best.getJSONObject("result").getDouble("estimator"))
        }.sortedBy { it.estimator }.distinctBy { it.id }.take(12)
        require(rows.isNotEmpty()) { "No usable counters" }
        return RaidReport(id, tier, level, System.currentTimeMillis(), (0 until variants.length()).map { variants.getJSONObject(it).getString("move1") }.distinct(),
            (0 until variants.length()).map { variants.getJSONObject(it).getString("move2") }.distinct(), rows)
    }
    fun encode(r: RaidReport): JSONObject = JSONObject().put("bossId", r.bossId).put("tier", r.tier).put("level", r.level).put("fetchedAt", r.fetchedAt)
        .put("fast", JSONArray(r.fast)).put("charged", JSONArray(r.charged)).put("counters", JSONArray(r.counters.map { c ->
            JSONObject().put("id", c.id).put("fast", c.fast.id).put("charged", c.charged.id).put("estimator", c.estimator)
        }))
    fun decode(j: JSONObject, meta: RaidMetadata): RaidReport {
        val a = j.getJSONArray("counters")
        return RaidReport(j.getString("bossId"), j.getString("tier"), j.getInt("level"), j.getLong("fetchedAt"),
            raidStrings(j.getJSONArray("fast")), raidStrings(j.getJSONArray("charged")), (0 until a.length()).map { i ->
                val c = a.getJSONObject(i); val id = c.getString("id")
                RaidCounter(id, meta.move(c.getString("fast"), meta.pokemon[id]), meta.move(c.getString("charged"), meta.pokemon[id]), c.getDouble("estimator"))
            })
    }
}

internal class RaidCounterRepository(private val context: Context) {
    private val directory = File(context.filesDir, "raid-counters-v1")
    fun metadata(): RaidMetadata = synchronized(lock) {
        shared ?: RaidJson.metadata(runCatching { JSONObject(AtomicFile(File(directory,"metadata.json")).openRead().bufferedReader().use { it.readText() }) }.getOrNull()
            ?: JSONObject(context.assets.open("raids/metadata.json").bufferedReader().use { it.readText() })).also { shared = it }
    }
    private fun file(id: String, tier: String, level: Int): File {
        require(id.matches(Regex("[A-Z0-9_]+")) && tier.matches(Regex("[A-Z0-9_]+")) && level in listOf(40, 50))
        return File(directory, "${id}_${tier}_${level}.json")
    }
    fun cached(id: String, tier: String, level: Int): RaidReport? = runCatching {
        AtomicFile(file(id,tier,level)).openRead().bufferedReader().use { RaidJson.decode(JSONObject(it.readText()), metadata()) }
            .also { require(it.bossId == id && it.tier == tier && it.level == level) }
    }.getOrNull()
    fun refresh(id: String, tier: String, level: Int): RaidReport {
        file(id,tier,level) // Validate path segments before network access.
        val report = RaidJson.report(get(counterPath(id,tier,level)), id,tier,level,metadata())
        write(file(id,tier,level), RaidJson.encode(report)); return report
    }
    fun catalog(refresh: Boolean = false): List<RaidChoice> {
        val target = File(directory, "catalog.json")
        val raw = if (refresh) get("raids") else runCatching { JSONObject(AtomicFile(target).openRead().bufferedReader().use { it.readText() }) }.getOrNull() ?: return emptyList()
        val tiers = raw.getJSONArray("tiers")
        val entries = (0 until tiers.length()).flatMap { i ->
            val tier = tiers.getJSONObject(i)
            if (tier.optString("type") != "RAID_TYPE_RAID" || listOf("LEGACY","FUTURE","UNSET","MAX").any { tier.getString("tier").contains(it) }) emptyList() else {
                val raids = tier.getJSONArray("raids")
                (0 until raids.length()).map { RaidChoice(raids.getJSONObject(it).getString("pokemonId"), tier.getString("tier")) }
            }
        }.distinct()
        require(entries.isNotEmpty()); if (refresh) write(target,raw)
        return entries
    }
    fun updateMetadata() {
        val all = get("pokemon").getJSONArray("pokemon")
        val allMoves = get("moves").getJSONArray("move")
        val root = JSONObject().put("fetchedAt",System.currentTimeMillis())
        root.put("pokemon",JSONArray((0 until all.length()).map { i ->
            val p=all.getJSONObject(i)
            JSONObject().put("id",p.getString("pokemonId")).put("types",JSONArray(listOf(p.optString("type"),p.optString("type2")).filter { it.isNotBlank() }))
                .put("stats",p.getJSONObject("stats")).put("fast",p.optJSONArray("quickMoves") ?: JSONArray())
                .put("charged",p.optJSONArray("cinematicMoves") ?: JSONArray())
                .put("elite",JSONArray(raidStrings(p.optJSONArray("eliteQuickMove"))+raidStrings(p.optJSONArray("eliteCinematicMove"))))
                .put("dex",p.optJSONObject("pokedex")?.optInt("pokemonNum") ?: 0)
        }))
        root.put("moves",JSONArray((0 until allMoves.length()).map { i -> val m=allMoves.getJSONObject(i)
            JSONObject().put("id",m.getString("moveId")).put("type",m.getString("type")) }))
        val parsed=RaidJson.metadata(root); require(parsed.pokemon.isNotEmpty() && parsed.moves.isNotEmpty())
        write(File(directory,"metadata.json"),root)
        synchronized(lock) { shared=parsed }
    }
    fun image(id: String): String? = synchronized(lock) {
        val map=images ?: JSONObject(context.assets.open("raids/images.json").bufferedReader().use { it.readText() }).also { images=it }
        map.optString(id).takeIf { it.isNotBlank() }?.let { "https://static.pokebattler.com/assets/pokemon/256/$it" }
    }
    private fun write(file: File, data: JSONObject) = synchronized(writeLock) {
        directory.mkdirs(); val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(data.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch(e: Exception) { atomic.failWrite(stream); throw e }
    }
    private fun get(path: String): JSONObject {
        val connection = URL("https://fight.pokebattler.com/$path").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000; connection.readTimeout = 25000
            require(connection.responseCode == 200)
            require(connection.contentType.orEmpty().contains("json")) { "Unexpected response" }
            val bytes = connection.inputStream.use { it.readBytesLimited(10 * 1024 * 1024) }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    companion object {
        private val lock = Any(); private val writeLock = Any(); private var shared: RaidMetadata? = null; private var images: JSONObject? = null
        fun counterPath(id: String, tier: String, level: Int) = "raids/defenders/$id/levels/$tier/attackers/levels/$level/strategies/CINEMATIC_ATTACK_WHEN_POSSIBLE/DEFENSE_RANDOM_MC?sort=ESTIMATOR&weatherCondition=NO_WEATHER&dodgeStrategy=DODGE_REACTION_TIME&aggregation=AVERAGE&includeLegendary=true&includeMegas=true&includeShadow=true"
    }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if(count < 0) break; require(out.size() + count <= limit); out.write(buffer,0,count) }
    return out.toByteArray()
}