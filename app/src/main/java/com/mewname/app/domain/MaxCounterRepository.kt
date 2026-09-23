package com.mewname.app.domain

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

internal enum class MaxRole(val sort: String) { ATTACK("ESTIMATOR"), GUARD("TANK"), HEAL("TOTAL_HEAL") }

/** Same defaults and response ordering as Pokebattler's public Max boss page. */
internal fun maxCounterPath(id: String, tier: String, role: MaxRole): String {
    require(id.matches(Regex("[A-Z0-9_]+")) && tier.matches(Regex("RAID_LEVEL_[0-9_]+MAX")))
    return "raids/defenders/" + id + "/levels/" + tier +
        "/attackers/levels/40/strategies/CINEMATIC_ATTACK_WHEN_POSSIBLE/DEFENSE_RANDOM_MC" +
        "?sort=" + role.sort + "&weatherCondition=NO_WEATHER&dodgeStrategy=DODGE_100" +
        "&aggregation=AVERAGE&includeLegendary=true&includeShadow=true" +
        "&attackerTypes=POKEMON_TYPE_ALL&adventureEffects=&numParty=1"
}

internal fun parseMaxCounters(raw: JSONObject, id: String, tier: String, meta: RaidMetadata): RaidReport {
    val boss = raw.getJSONArray("attackers").getJSONObject(0)
    require(boss.getString("pokemonId") == id && boss.getString("boss") == tier)
    val defenders = boss.getJSONObject("randomMove").getJSONArray("defenders")
    // The site reverses both arrays. Do not re-sort by estimator: Guard and Heal use other metrics.
    val counters = (defenders.length() - 1 downTo 0).map { index ->
        val entry = defenders.getJSONObject(index)
        val moves = entry.getJSONArray("byMove")
        require(moves.length() > 0)
        val best = moves.getJSONObject(moves.length() - 1)
        val pokemon = entry.getString("pokemonId")
        RaidCounter(pokemon, meta.move(best.getString("move1"), meta.pokemon[pokemon]),
            meta.move(best.getString("move2"), meta.pokemon[pokemon]),
            best.optJSONObject("result")?.optDouble("estimator", 0.0) ?: 0.0)
    }
    require(counters.isNotEmpty()) { "No Max counters returned" }
    return RaidReport(id, tier, 40, System.currentTimeMillis(), emptyList(), emptyList(), counters)
}

internal class MaxCounterRepository(context: Context) {
    private val raid = RaidCounterRepository(context.applicationContext)
    private val directory = File(context.filesDir, "max-counters-v1")
    fun metadata() = raid.metadata()
    private fun file(id: String, tier: String, role: MaxRole): AtomicFile {
        maxCounterPath(id, tier, role)
        return AtomicFile(File(directory, id + "_" + tier + "_" + role.name + ".json"))
    }
    fun tier(id: String): String? =
        (raid.catalog() + raid.savedCatalog()).filter { it.id == id && it.tier.endsWith("_MAX") }
            .firstOrNull()?.tier
    fun cached(id: String, tier: String, role: MaxRole): RaidReport? = runCatching {
        val report = file(id, tier, role).openRead().bufferedReader().use { RaidJson.decode(JSONObject(it.readText()), metadata()) }
        require(report.bossId == id && report.tier == tier)
        report
    }.getOrNull()
    fun refresh(id: String, tier: String, role: MaxRole): RaidReport {
        val report = parseMaxCounters(raid.get(maxCounterPath(id, tier, role)), id, tier, metadata())
        directory.mkdirs()
        val target = file(id, tier, role)
        val stream = target.startWrite()
        try { stream.write(RaidJson.encode(report).toString().toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        return report
    }
}