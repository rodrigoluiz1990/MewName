package com.mewname.app

import com.mewname.app.domain.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class RaidRotationTest {
    private fun event(id: String, category: String, start: String, end: String) = CalendarEvent(
        id, id, category, category, LocalDateTime.parse(start), LocalDateTime.parse(end), null,
        "https://www.pokebattler.com/raids/$id")
    private val events = listOf(
        event("DIALGA", "raid", "2026-10-14T06:00", "2026-10-20T22:00"),
        event("PALKIA", "raid", "2026-10-21T06:00", "2026-10-27T22:00"),
        event("DRAGONITE_MEGA", "raid_mega", "2026-10-14T06:00", "2026-10-20T22:00"),
        event("CHARIZARD_MEGA_X", "raid_mega", "2026-10-21T06:00", "2026-10-27T22:00"),
        event("CHARIZARD_MEGA_Y", "raid_mega", "2026-10-21T06:00", "2026-10-27T22:00"))
    private val raw = listOf(RaidChoice("DIALGA", "RAID_LEVEL_5"), RaidChoice("PALKIA", "RAID_LEVEL_5"),
        RaidChoice("DRAGONITE_MEGA", "RAID_LEVEL_MEGA"), RaidChoice("CHARIZARD_MEGA_X", "RAID_LEVEL_MEGA"))
    private fun at(time: String) = scheduledRaidChoices(raw, events, LocalDateTime.parse(time)).map { it.id }

    @Test fun futureBossWithoutDatesIsNotCurrent() {
        assertEquals(listOf("DIALGA", "DRAGONITE_MEGA"), at("2026-10-20T21:30"))
    }
    @Test fun intervalIsStartInclusiveAndEndExclusive() {
        assertTrue(at("2026-10-14T05:59:59").isEmpty())
        assertEquals(listOf("DIALGA", "DRAGONITE_MEGA"), at("2026-10-14T06:00"))
        assertTrue(at("2026-10-20T22:00").isEmpty())
        assertTrue(at("2026-10-21T05:59:59").isEmpty())
        assertEquals(listOf("PALKIA", "CHARIZARD_MEGA_X", "CHARIZARD_MEGA_Y"), at("2026-10-21T06:00"))
    }
    @Test fun rotationCanAddScheduledBossMissingFromSimulator() {
        assertEquals(2, scheduledRaidChoices(emptyList(), events, LocalDateTime.parse("2026-10-20T12:00")).size)
    }
    @Test fun missingScheduleDoesNotClaimAvailabilityAndPreservesOtherTiers() {
        val other = listOf(RaidChoice("AXEW", "RAID_LEVEL_1"), RaidChoice("SQUIRTLE", "RAID_LEVEL_1_MAX"),
            RaidChoice("LANDORUS", "RAID_LEVEL_5_SHADOW"))
        assertEquals(other, scheduledRaidChoices(raw + other, emptyList(), LocalDateTime.parse("2026-10-20T12:00")))
    }
    @Test fun raidHourAndUntrustedLinksCannotCreateRotations() {
        val now = LocalDateTime.parse("2026-10-20T12:00")
        val candidates = listOf(events[0].copy(category = "raid_hour"),
            events[0].copy(link = "https://other.example/raids/MEWTWO"),
            events[0].copy(link = "https://pokebattler.com/pokemon/MEWTWO"))
        assertTrue(scheduledRaidChoices(raw, candidates, now).isEmpty())
    }
    @Test fun yearBoundaryAndDuplicateEvents() {
        val event = event("DIALGA", "raid", "2026-12-28T06:00", "2027-01-04T22:00")
        assertEquals(listOf(RaidChoice("DIALGA", "RAID_LEVEL_5")),
            scheduledRaidChoices(raw, listOf(event, event), LocalDateTime.parse("2027-01-01T12:00")))
    }
}
