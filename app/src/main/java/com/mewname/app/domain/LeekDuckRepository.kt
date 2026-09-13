package com.mewname.app.domain

import android.content.Context
import android.util.AtomicFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

enum class LeekSection(val path: String) { ROCKET("rocket-lineups"), EGGS("eggs"), CODES("promo-codes"), RESEARCH("research");
    val url: String get() = "https://leekduck.com/$path/"
}
data class LeekPokemon(val name: String, val image: String, val shiny: Boolean = false,
    val detail: String = "", val rarity: Int? = null)
data class LeekSlot(val position: Int, val encounter: Boolean, val pokemon: List<LeekPokemon>)
data class LeekEntry(val id: String, val title: String, val group: String, val description: String = "",
    val image: String = "", val pokemon: List<LeekPokemon> = emptyList(), val slots: List<LeekSlot> = emptyList(),
    val code: String = "", val expires: Long? = null, val expired: Boolean = false,
    val unknownExpiry: Boolean = false)
data class LeekCatalog(val entries: List<LeekEntry>, val sourceUpdated: String, val fetchedAt: Long)

internal object LeekDuckParser {
    fun parse(section: LeekSection, html: String, fetchedAt: Long): LeekCatalog {
        val doc = Jsoup.parse(html, section.url)
        fun Element.image() = selectFirst("img[src]")?.absUrl("src").orEmpty()
        fun Element.pokemon(): LeekPokemon {
            val name = attr("data-pokemon").ifBlank { selectFirst(".name, .reward-label")?.text().orEmpty() }
            val weak = listOf(attr("data-double-weaknesses").takeIf { it.isNotBlank() }?.let { "$it (2×)" },
                attr("data-single-weaknesses").takeIf { it.isNotBlank() }).filterNotNull().joinToString("; ")
            return LeekPokemon(pokemonDisplayName(name), image(), selectFirst(".shiny-icon") != null,
                selectFirst(".cp-range")?.text() ?: weak,
                select(".rarity .mini-egg").size.takeIf { it > 0 })
        }
        val entries = when (section) {
            LeekSection.ROCKET -> doc.select(".rocket-profile").mapIndexed { index, el ->
                val title = el.selectFirst(".employee-info .name")?.text().orEmpty()
                val role = el.selectFirst(".employee-info .title")?.text().orEmpty()
                val slots = el.select(".lineup-info .slot").map { slot ->
                    LeekSlot(slot.selectFirst(".number")?.text()?.toIntOrNull()
                        ?: (slot.elementSiblingIndex() + 1), slot.hasClass("encounter"),
                        slot.select(".shadow-pokemon[data-pokemon]").map { it.pokemon() })
                }
                require(title.isNotBlank() && slots.size == 3 && slots.all { it.pokemon.isNotEmpty() }) { "Incomplete Rocket lineup" }
                LeekEntry("rocket-$index", title, when { role.contains("Boss") -> "Giovanni"; role.contains("Leader") -> "Leaders"; else -> "Grunts" },
                    el.selectFirst(".quote-text")?.text().orEmpty(), el.selectFirst(".employee-info")?.image().orEmpty(), slots = slots)
            }
            LeekSection.EGGS -> doc.select(".egg-grid").mapIndexed { index, grid ->
                val before = generateSequence(grid.previousElementSibling()) { it.previousElementSibling() }.toList()
                val heading = before.firstOrNull { it.tagName() == "h2" } ?: error("Missing egg group")
                val notes = before.takeWhile { it != heading }.filter { it.tagName() == "p" || it.hasClass("egg-section") }.reversed().joinToString(" ") { it.text() }
                val pokemon = grid.select(".pokemon-card").map { it.pokemon() }
                require(pokemon.isNotEmpty() && pokemon.all { it.name.isNotBlank() }) { "Incomplete egg group" }
                LeekEntry("eggs-$index", heading.text(), heading.text(), notes, pokemon = pokemon)
            }
            LeekSection.RESEARCH -> doc.select(".task-category").flatMapIndexed { categoryIndex, category ->
                val group = category.selectFirst("h2")?.text().orEmpty()
                val event = category.hasClass("event-field-research")
                val expiry = runCatching { OffsetDateTime.parse(category.attr("data-event-end"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant().toEpochMilli() }.getOrNull()
                category.select(".task-item").mapIndexed { index, task ->
                    val title = task.selectFirst(".task-text")?.text().orEmpty()
                    val rewards = task.select(".reward-list > .reward").map { reward ->
                        val name = reward.selectFirst(".reward-label")?.text().orEmpty()
                        LeekPokemon(name, reward.selectFirst("img.reward-image")?.absUrl("src") ?: reward.image(),
                            reward.selectFirst(".shiny-icon") != null,
                            reward.selectFirst(".cp-values")?.text().orEmpty())
                    }
                    require(title.isNotBlank() && group.isNotBlank() && rewards.isNotEmpty() && rewards.all { it.name.isNotBlank() }) {
                        "Incomplete research task"
                    }
                    LeekEntry("research-$categoryIndex-$index", title, group,
                        description = if (event) "Event" else "Regular", pokemon = rewards, expires = expiry)
                }
            }
            LeekSection.CODES -> doc.select(".promo-card").mapIndexed { index, el ->
                val code = el.selectFirst(".code-display .text")?.text().orEmpty()
                val title = el.selectFirst("h3.title")?.text().orEmpty()
                require(code.isNotBlank() && title.isNotBlank()) { "Incomplete promo code" }
                val expiry = el.selectFirst(".expiry")
                val hidden = expiry?.attr("data-hide-expiry") == "true"
                val parsedDate = runCatching { OffsetDateTime.parse(expiry?.attr("data-expires"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")).toInstant().toEpochMilli() }.getOrNull()
                val rewards = el.select(".reward-list > li").map { reward ->
                    LeekPokemon(reward.selectFirst(".reward-label")?.text().orEmpty().ifBlank {
                        reward.selectFirst("img[alt]")?.attr("alt").orEmpty().ifBlank { reward.text() }
                    }, reward.image(), detail = reward.selectFirst(".quantity")?.text().orEmpty())
                }
                LeekEntry("code-$index", title, "Codes", el.selectFirst(".description")?.text().orEmpty(), pokemon = rewards,
                    code = code, expires = parsedDate.takeUnless { hidden }, unknownExpiry = hidden || parsedDate == null,
                    expired = el.hasClass("expired") || el.hasClass("-expired") || expiry?.text()?.equals("Expired", true) == true)
            }
        }
        require(entries.isNotEmpty()) { "Leek Duck returned no recognizable entries" }
        val updated = Regex("Updated on ([A-Za-z]+ \\d{1,2}, \\d{4})").find(doc.text())?.groupValues?.get(1).orEmpty()
        return LeekCatalog(entries, updated, fetchedAt)
    }
}

internal class LeekDuckRepository(context: Context) {
    private val directory = File(context.filesDir, "leekduck").apply { mkdirs() }
    private fun file(section: LeekSection) = AtomicFile(File(directory, "${section.path}.json"))
    fun cached(section: LeekSection): LeekCatalog? = runCatching {
        val json = JSONObject(file(section).openRead().bufferedReader().use { it.readText() })
        LeekDuckParser.parse(section, json.getString("html"), json.getLong("fetchedAt"))
    }.getOrNull()
    fun refresh(section: LeekSection): LeekCatalog {
        val connection = URL(section.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000; connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "MewName/1.0 (+https://leekduck.com/)")
        val html = try {
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val read = input.read(buffer); if (read < 0) break
                    require(output.size() + read <= 3_000_000) { "Response too large" }; output.write(buffer, 0, read) }
                output.toString("UTF-8")
            }
        } finally { connection.disconnect() }
        return storeValidated(section, html, System.currentTimeMillis())
    }
    internal fun storeValidated(section: LeekSection, html: String, time: Long): LeekCatalog {
        val catalog = LeekDuckParser.parse(section, html, time)
        val target = file(section); val stream = target.startWrite()
        try {
            stream.write(JSONObject().put("html", html).put("fetchedAt", time).toString().toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (e: Exception) { target.failWrite(stream); throw e }
        return catalog
    }
}
