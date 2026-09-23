package com.mewname.app.domain

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

internal const val CALENDAR_BASE_URL = "https://rodrigoluiz1990.github.io/laboratorio-do-sam/Calendario/"

internal data class CalendarEvent(
    val id: String, val title: String, val category: String, val categoryLabel: String,
    val start: LocalDateTime, val end: LocalDateTime, val image: String?, val link: String?
) {
    fun occursOn(day: LocalDate): Boolean = !start.toLocalDate().isAfter(day) && end.isAfter(day.atStartOfDay())
}
internal data class CalendarCatalog(val events: List<CalendarEvent>, val fetchedAt: Long)

internal object CalendarParser {
    fun date(value: String): LocalDateTime = runCatching {
        OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }.getOrElse {
        if (value.length == 10) LocalDate.parse(value).atStartOfDay() else LocalDateTime.parse(value)
    }

    fun url(value: String): String? = runCatching {
        if (value.isBlank()) return null
        URI(CALENDAR_BASE_URL).resolve(value).takeIf { it.scheme in setOf("https", "http") && it.host != null }?.toString()
    }.getOrNull()

    fun parse(yearData: JSONObject, references: JSONObject, meta: JSONObject): List<CalendarEvent> {
        val categories = meta.optJSONObject("meta")?.optJSONObject("categorias") ?: JSONObject()
        val refs = mutableMapOf<String, JSONObject>()
        references.keys().forEach { category ->
            val list = references.optJSONArray(category) ?: JSONArray()
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                entry.keys().forEach { key -> entry.optJSONObject(key)?.let { refs["$category/$key"] = it } }
            }
        }
        return buildList {
            yearData.keys().forEach { year ->
                val block = yearData.optJSONObject(year) ?: return@forEach
                for (category in block.keys()) {
                    val list = block.optJSONArray(category) ?: continue
                    val categoryMeta = categories.optJSONObject(category)
                    for (i in 0 until list.length()) {
                        val event = list.optJSONObject(i) ?: error("Invalid event in $year/$category")
                        val ref = event.optString("ref")
                        val info = if (ref.isBlank()) event else refs["${if (category == "raid_hour") "raid" else category}/$ref"]
                            ?: error("Unknown calendar reference: $ref")
                        val title = event.optString("titulo_alt").ifBlank { event.optString("titulo") }.ifBlank { info.optString("titulo") }
                        require(title.isNotBlank()) { "Event without a title" }
                        val start = date(event.getString("inicio"))
                        val end = date(event.getString("fim"))
                        require(!end.isBefore(start)) { "Invalid calendar interval" }
                        val image = event.optString("img").ifBlank { info.optString("img") }.ifBlank { categoryMeta?.optString("icon").orEmpty() }
                        add(CalendarEvent("$category|$title|$start|$end", title, category,
                            categoryMeta?.optString("label")?.takeIf { it.isNotBlank() } ?: when(category) {
                                "season" -> "Temporada"; "go_pass" -> "Passe GO"; else -> category
                            }, start, end, url(image), url(event.optString("link").ifBlank { info.optString("link") })))
                    }
                }
            }
        }.distinctBy { it.id }.sortedWith(compareBy<CalendarEvent> { it.start }.thenBy { it.title })
    }
}

internal class CalendarRepository(context: Context, private val fetch: ((String, Boolean) -> JSONObject)? = null) {
    private val directory = File(context.filesDir, "calendar")
    private fun file(year: Int) = AtomicFile(File(directory, "$year.json"))
    private fun decode(json: JSONObject) = CalendarCatalog(
        CalendarParser.parse(json.getJSONObject("years"), json.getJSONObject("references"), json.getJSONObject("meta")),
        json.getLong("fetchedAt")
    )
    fun cached(year: Int): CalendarCatalog? = runCatching {
        file(year).openRead().bufferedReader().use { decode(JSONObject(it.readText())) }
    }.getOrNull()

    fun refresh(year: Int): CalendarCatalog {
        val years = get("$year.json")
        val previous = get("${year - 1}.json", optional = true)
        // Only carry over previous-year events that actually reach this year.
        // Old references may no longer exist in the source's current raid catalog.
        previous.keys().forEach { key ->
            val original = previous.optJSONObject(key) ?: return@forEach
            val carry = JSONObject()
            original.keys().forEach { category ->
                val list = original.optJSONArray(category) ?: JSONArray()
                val kept = JSONArray()
                for (i in 0 until list.length()) {
                    val event = list.optJSONObject(i) ?: continue
                    // The historical feed includes placeholders with empty dates.
                    // They cannot be carried into this year and must not block its events.
                    val end = runCatching { CalendarParser.date(event.optString("fim")) }.getOrNull()
                        ?: continue
                    if (end.isAfter(LocalDate.of(year, 1, 1).atStartOfDay())) kept.put(event)
                }
                carry.put(category, kept)
            }
            years.put(key, carry)
        }
        val snapshot = JSONObject().put("years", years).put("references", get("raids.json"))
            .put("meta", get("meta.json")).put("fetchedAt", System.currentTimeMillis())
        val catalog = decode(snapshot)
        directory.mkdirs()
        val target = file(year)
        val stream = target.startWrite()
        try { stream.write(snapshot.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
        return catalog
    }

    private fun get(path: String, optional: Boolean = false): JSONObject {
        fetch?.let { return it(path, optional) }
        val connection = URL(CALENDAR_BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            if (optional && connection.responseCode == 404) return JSONObject()
            check(connection.responseCode == 200) { "Calendar HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText().removePrefix("\uFEFF")) }
        } finally { connection.disconnect() }
    }
}
