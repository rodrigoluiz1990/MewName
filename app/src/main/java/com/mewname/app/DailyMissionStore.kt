package com.mewname.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

internal data class DailyMission(val id: String = UUID.randomUUID().toString(), val name: String,
    val icon: String, val completedDay: String = "") {
    fun completed(today: LocalDate = LocalDate.now()) = completedDay == today.toString()
}

internal fun defaultDailyMissions(language: com.mewname.app.domain.AppLanguage) = listOf(
    Triple("Capturar Pokémon", "Catch a Pokémon", "Capturar Pokémon") to "pokeball_sprite.png",
    Triple("Girar PokéStop", "Spin a PokéStop", "Girar una Poképarada") to "pokestop.png",
    Triple("Receber 50 moedas", "Receive 50 coins", "Recibir 50 monedas") to "Item_COIN_01.png",
    Triple("Aumentar amizade com o companheiro", "Increase buddy friendship", "Aumentar amistad con el compañero") to "Buddy_SingleHeart_Full.png",
    Triple("Marcar tarefa diária", "Claim daily research stamp", "Marcar tarea diaria") to "daily_research_box.png",
    Triple("Fazer raid", "Complete a raid", "Hacer una incursión") to "Item_1401.png"
).mapIndexed { index, (names, icon) -> DailyMission("default-$index", lt(language, names.first, names.second, names.third), "mission_icons/$icon") }

internal class DailyMissionStore(context: Context) {
    val preferences = context.getSharedPreferences("daily_missions", Context.MODE_PRIVATE)
    fun initializeDefaults(defaults: List<DailyMission>) {
        if (!preferences.contains("missions")) write(defaults)
    }
    fun all(): List<DailyMission> = runCatching {
        val array = JSONArray(preferences.getString("missions", "[]"))
        (0 until array.length()).map { index -> array.getJSONObject(index).let {
            DailyMission(it.getString("id"), it.getString("name"), it.getString("icon"), it.optString("completedDay"))
        } }
    }.getOrDefault(emptyList())
    fun save(mission: DailyMission) {
        val list = all().toMutableList()
        val index = list.indexOfFirst { it.id == mission.id }
        if (index < 0) list.add(mission) else list[index] = mission
        write(list)
    }
    fun move(id: String, delta: Int) {
        val list = all().toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val destination = (index + delta).coerceIn(0, list.lastIndex)
        if (index == destination) return
        list.add(destination, list.removeAt(index))
        write(list)
    }
    fun delete(id: String) = write(all().filterNot { it.id == id })
    fun complete(id: String, done: Boolean, today: LocalDate = LocalDate.now()) = write(all().map {
        if (it.id == id) it.copy(completedDay = if (done) today.toString() else "") else it
    })
    private fun write(list: List<DailyMission>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("icon", it.icon).put("completedDay", it.completedDay)) }
        preferences.edit().putString("missions", array.toString()).apply()
    }
}
