package com.mewname.app

import com.mewname.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HomeEventArtTest {
    private val today = LocalDate.of(2026, 10, 12)
    private fun event(slug: String, start: LocalDate = today, end: LocalDate = today) =
        HomeEventArt(slug, "https://pokemongo.com/en/news/$slug", "https://lh3.googleusercontent.com/art", start, end, HomeEventArtParser.priority(slug))
    private fun news(slug: String = "halloween-2026") = HomeEventArtParser.News("Evento", "https://pokemongo.com/pt-BR/news/$slug", "https://lh3.googleusercontent.com/art")

    @Test fun communityAndGigantamaxBeatWeeklyEvent() {
        val week = event("halloween", today.minusDays(2), today.plusDays(5))
        val mega = event("gigantamax-2026")
        val community = event("communityday-october-2026-zorua")
        assertEquals(mega, HomeEventArtParser.select(listOf(week, mega), today))
        assertEquals(community, HomeEventArtParser.select(listOf(mega, week, community), today))
        assertEquals(week, HomeEventArtParser.select(listOf(community, week, mega), today.plusDays(1)))
    }
    @Test fun futureAndExpiredNeverWinAndLastDayIsIncluded() {
        val active = event("season", today.minusDays(10), today)
        assertEquals(active, HomeEventArtParser.select(listOf(event("community-day", today.plusDays(1), today.plusDays(1)),
            event("gigantamax", today.minusDays(1), today.minusDays(1)), active), today))
        assertNull(HomeEventArtParser.select(listOf(active), today.plusDays(1)))
    }
    @Test fun shorterEventWinsStableTieRegardlessOfFeedOrder() {
        val long = event("weekly-a", today.minusDays(2), today.plusDays(2))
        val short = event("weekly-b")
        assertEquals(short, HomeEventArtParser.select(listOf(long, short), today))
        assertEquals(short, HomeEventArtParser.select(listOf(short, long), today))
    }
    @Test fun parsesActualEventWindowNotPublicationDate() {
        val html = """<meta property="datePublished" content="2026-10-08"><main><p>Sunday, November 1, at 10:00 a.m. to Thursday, November 5, 2026, at 8:00 p.m. local time</p></main>"""
        val parsed = HomeEventArtParser.article(news(), html)!!
        assertEquals(LocalDate.of(2026, 11, 1), parsed.start)
        assertEquals(LocalDate.of(2026, 11, 5), parsed.end)
        assertNull(HomeEventArtParser.select(listOf(parsed), today))
    }
    @Test fun parsesSingleDayAndYearBoundary() {
        assertEquals(today, HomeEventArtParser.article(news(), "<main><p>Monday, October 12, 2026, from 2:00 p.m. to 5:00 p.m. local time</p></main>")!!.end)
        val range = HomeEventArtParser.article(news(), "<main><p>December 30 to January 2, 2027, local time</p></main>")!!
        assertEquals(LocalDate.of(2026, 12, 30), range.start)
        assertEquals(LocalDate.of(2027, 1, 2), range.end)
    }
    @Test fun ambiguousOrRegionalArticlesAreSkipped() {
        assertNull(HomeEventArtParser.article(news(), "<main><p>Published October 12, 2026</p></main>"))
        assertNull(HomeEventArtParser.article(news(), "<main><p>October 12, 2026, at 10:00 UTC</p></main>"))
        assertNull(HomeEventArtParser.article(news("city-safari-2026"), "<main><p>October 12, 2026, local time</p></main>"))
    }
    @Test fun extractsOfficialCardArtAndRejectsNavigationAndExternalLinks() {
        val html = """<main><a href="/pt_BR/news/event-2026"><img src="https://lh3.googleusercontent.com/art=s0-e365"><div class="_newsCardTitle_123">Evento</div></a><a href="/events"><img src="/logo.png"></a><a href="https://example.org/news/bad"><img src="https://lh3.googleusercontent.com/wrong"></a></main>"""
        val result = HomeEventArtParser.news(html, "https://pokemongo.com/pt-BR/news")
        assertEquals(1, result.size)
        assertEquals("Evento", result.single().title)
        assertEquals("https://lh3.googleusercontent.com/art=w1440", result.single().image)
    }
    @Test fun usesYearFromOfficialSlugWhenEventWindowOmitsIt() {
        val pass = HomeEventArtParser.article(news("go-pass-october-2026"),
            "<main><p>From Tuesday, October 6, at 10:00 a.m. to Tuesday, November 3, at 10:00 a.m. local time, the GO Pass will be available!</p></main>")!!
        assertEquals(LocalDate.of(2026, 10, 6), pass.start)
        assertEquals(LocalDate.of(2026, 11, 3), pass.end)
        assertEquals(100, pass.priority)
        assertEquals(500, HomeEventArtParser.priority("communitydayclassic-gible-september-2026"))
        assertEquals(600, HomeEventArtParser.priority("gofest-2026-global"))
    }
    private fun seasonPage(window: String = "September 8 at 10:00 a.m. to December 1, 2026, at 10:00 a.m. local time", id: String = "twilight-trails") = """
        <meta property="og:url" content="https://pokemongolive.com/seasons/$id">
        <meta property="og:title" content="Trilhas do Entardecer">
        <meta property="og:image" content="https://lh3.googleusercontent.com/season">
        <main><details><p>What are Seasons?</p></details><div class="size:subheading"><b>$window</b></div></main>
    """
    @Test fun seasonUsesOfficialMetadataAndHeroDates() {
        val season = HomeEventArtParser.season(seasonPage("De setembro a dezembro"), seasonPage(), "pt-BR")!!
        assertEquals("Trilhas do Entardecer", season.title)
        assertEquals("https://pokemongo.com/pt-BR/seasons/twilight-trails", season.link)
        assertEquals("https://lh3.googleusercontent.com/season=w1440", season.image)
        assertEquals(LocalDate.of(2026, 9, 8), season.start)
        assertEquals(LocalDate.of(2026, 12, 1), season.end)
        assertEquals(50, season.priority)
        assertEquals(season, HomeEventArtParser.select(listOf(season), today))
        assertNull(HomeEventArtParser.select(listOf(season), season.start.minusDays(1)))
        assertNull(HomeEventArtParser.select(listOf(season), season.end.plusDays(1)))
    }
    @Test fun seasonRemainsLastBehindPassAndDayEvents() {
        val season = HomeEventArtParser.season(seasonPage(), seasonPage(), "en")!!
        val pass = event("go-pass-october-2026").copy(image = "pass")
        val community = event("community-day-2026").copy(image = "community")
        assertEquals(listOf(community, pass, season), HomeEventArtParser.active(listOf(season, pass, community), today))
    }
    @Test fun seasonRejectsMismatchedPagesAndMissingOrUntrustedArt() {
        assertNull(HomeEventArtParser.season(seasonPage(), seasonPage(id = "next-season"), "en"))
        assertNull(HomeEventArtParser.season(seasonPage().replace("lh3.googleusercontent.com", "example.org"), seasonPage(), "en"))
        assertNull(HomeEventArtParser.season(seasonPage(), seasonPage("September 8, 2026"), "en"))
        assertNull(HomeEventArtParser.season(seasonPage().replace("pokemongolive.com", "example.org"), seasonPage(), "en"))
    }
    @Test fun seasonHandlesYearRolloverAndRejectsInvalidRanges() {
        val page = seasonPage("December 1 to March 1, 2027, local time")
        assertEquals(LocalDate.of(2026, 12, 1), HomeEventArtParser.season(page, page, "en")!!.start)
        val invalid = seasonPage("January 1 to December 1, 2026, local time")
        assertNull(HomeEventArtParser.season(invalid, invalid, "en"))
    }
}
