package com.mewname.app

import com.mewname.app.domain.CalendarParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CalendarParserTest {
    private val refs = JSONObject("""{"raid":[{"raid_x":{"titulo":"Raid X","link":"https://example.com/event","img":"../Assets/x.png"}}]}""")
    private val meta = JSONObject("""{"meta":{"categorias":{"raid_hour":{"label":"Hora Lendária","icon":"../Assets/hour.png"}}}}""")

    @Test fun resolvesRaidReferencesAndAlternativeTitles() {
        val year = JSONObject("""{"2026":{"raid_hour":[{"ref":"raid_x","titulo_alt":"Hora lendária: X","inicio":"2026-09-30T18:00:00","fim":"2026-09-30T19:00:00"}]}}""")
        val event = CalendarParser.parse(year, refs, meta).single()
        assertEquals("Hora lendária: X", event.title)
        assertEquals("Hora Lendária", event.categoryLabel)
        assertEquals("https://example.com/event", event.link)
        assertEquals("https://rodrigoluiz1990.github.io/laboratorio-do-sam/Assets/x.png", event.image)
        assertEquals(18, event.start.hour)
    }

    @Test fun includesSpanningEventsAndExcludesMidnightAfterTheyEnd() {
        val year = JSONObject("""{"2025":{"events":[{"titulo":"Ano novo","inicio":"2025-12-31T10:00:00","fim":"2026-01-02T00:00:00"}]}}""")
        val event = CalendarParser.parse(year, JSONObject(), JSONObject()).single()
        assertTrue(event.occursOn(LocalDate.of(2026,1,1)))
        assertFalse(event.occursOn(LocalDate.of(2026,1,2)))
        assertFalse(event.occursOn(LocalDate.of(2025,12,30)))
    }

    @Test fun acceptsDateOnlyValuesAndRejectsNonWebLinks() {
        assertEquals(0, CalendarParser.date("2026-09-20").hour)
        assertNull(CalendarParser.url("javascript:alert(1)"))
        assertNull(CalendarParser.url(""))
    }

    @Test fun parsesActualPublishedCalendarSnapshot() {
        fun fixture(name: String) = javaClass.getResourceAsStream("/calendar/$name.json")!!.bufferedReader().use { JSONObject(it.readText().removePrefix("\uFEFF")) }
        val events = CalendarParser.parse(fixture("2026"), fixture("raids"), fixture("meta"))
        assertEquals(14, events.map { it.category }.distinct().size)
        assertTrue(events.any { it.category == "season" })
        assertTrue(events.any { it.category == "dynamax" })
        assertTrue(events.any { it.category == "raid_hour" && it.title.startsWith("Hora") })
        assertTrue(events.all { it.title.isNotBlank() && !it.end.isBefore(it.start) })
    }
    @Test fun currentSourceContainsEventsForReportedDay() {
        fun fixture(name: String) = javaClass.getResourceAsStream("/calendar/current-" + name + ".json")!!
            .bufferedReader().use { JSONObject(it.readText().removePrefix("\uFEFF")) }
        val events = CalendarParser.parse(fixture("2026"), fixture("raids"), fixture("meta"))
        val day = events.filter { it.occursOn(LocalDate.of(2026, 9, 21)) }
        assertEquals(12, day.size)
        assertTrue(day.any { it.title.contains("Horizontes") })
        assertEquals(3, day.count { it.category == "max_monday" })
    }
    @Test fun completeRefreshIncludesTodayAndPersistsCache() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val repository = com.mewname.app.domain.CalendarRepository(context) { path, _ ->
            javaClass.getResourceAsStream("/calendar/current-" + path)!!
                .bufferedReader().use { JSONObject(it.readText().removePrefix("\uFEFF")) }
        }
        val catalog = repository.refresh(2026)
        assertEquals(12, catalog.events.count { it.occursOn(LocalDate.of(2026, 9, 21)) })
        assertTrue(catalog.events.any { it.start.year == 2025 && it.occursOn(LocalDate.of(2026, 1, 1)) })
        assertEquals(catalog, repository.cached(2026))
    }
}
