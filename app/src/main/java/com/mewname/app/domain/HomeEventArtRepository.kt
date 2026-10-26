package com.mewname.app.domain

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class HomeEventArt(val title: String, val link: String, val image: String,
    val start: LocalDate, val end: LocalDate, val priority: Int)
internal data class HomeEventArtCatalog(val events: List<HomeEventArt>, val fetchedAt: Long)

internal object HomeEventArtParser {
    private val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val date = Regex("\\b(" + months.joinToString("|") + ")\\s+(\\d{1,2})(?:,?\\s+(20\\d{2}))?\\b", RegexOption.IGNORE_CASE)
    fun priority(slug: String): Int = when {
        ("go-fest" in slug || "gofest" in slug || "go-tour" in slug || "gotour" in slug || "gowildarea" in slug) && "global" in slug -> 600
        ("community-day" in slug || "communityday" in slug) -> 500
        "gigantamax" in slug || "max-battle-day" in slug -> 450
        "raid-day" in slug || "hatch-day" in slug -> 400
        "spotlight" in slug || "raid-hour" in slug || "max-monday" in slug -> 350
        "go-pass" in slug || "season" in slug -> 100
        else -> 300
    }
    fun select(events: List<HomeEventArt>, today: LocalDate): HomeEventArt? = active(events, today).firstOrNull()
    fun active(events: List<HomeEventArt>, today: LocalDate): List<HomeEventArt> = events
        .filter { !today.isBefore(it.start) && !today.isAfter(it.end) }
        .sortedWith(compareByDescending<HomeEventArt> { it.priority }
            .thenBy { ChronoUnit.DAYS.between(it.start, it.end) }.thenByDescending { it.start }.thenBy { it.link })
        .distinctBy { it.image }

    data class News(val title: String, val link: String, val image: String)
    fun season(localizedHtml: String, englishHtml: String, locale: String): HomeEventArt? {
        val localized = Jsoup.parse(localizedHtml)
        val english = Jsoup.parse(englishHtml)
        fun slug(doc: org.jsoup.nodes.Document): String? {
            val url = doc.selectFirst("meta[property=og:url]")?.attr("content") ?: return null
            val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
            if (uri.host !in listOf("pokemongo.com", "pokemongolive.com")) return null
            return uri.path.substringAfter("/seasons/", "").trim('/').takeIf { it.isNotBlank() && '/' !in it }
        }
        val id = slug(localized) ?: return null
        if (id != slug(english)) return null
        val title = localized.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: return null
        val image = localized.selectFirst("meta[property=og:image]")?.attr("content") ?: return null
        if (!image.startsWith("https://lh3.googleusercontent.com/")) return null
        val window = english.select("main b, main p").firstOrNull {
            it.text().contains("local time", true) && date.findAll(it.text()).count() == 2
        }?.text() ?: return null
        val item = News(title, "https://pokemongo.com/$locale/seasons/$id", image.substringBefore('=') + "=w1440")
        return article(item, "<main><p>${org.jsoup.nodes.Entities.escape(window)}</p></main>")?.copy(priority = 50)
    }
    fun news(html: String, base: String): List<News> = Jsoup.parse(html, base).select("main a[href]")
        .mapNotNull { a ->
            val link = a.absUrl("href")
            val image = a.selectFirst("img[src]")?.absUrl("src") ?: return@mapNotNull null
            if (!link.startsWith("https://pokemongo.com/") || "/news/" !in link || !image.startsWith("https://lh3.googleusercontent.com/")) return@mapNotNull null
            val title = a.selectFirst("[class*=newsCardTitle]")?.text() ?: a.text()
            News(title, link, image.substringBefore('=') + "=w1440")
        }.distinctBy { it.link.substringAfterLast('/') }.take(30)

    /** Annual meteor showers have separate windows, not one continuous event. */
    fun articles(news: News, englishHtml: String, currentYear: Int = LocalDate.now().year): List<HomeEventArt> {
        val slug = news.link.substringAfterLast('/')
        if (!slug.startsWith("minior-meteor-showers-")) return listOfNotNull(article(news, englishHtml))
        val announcedYear = Regex("20[0-9]{2}").find(slug)?.value?.toIntOrNull() ?: return emptyList()
        val paragraphs = Jsoup.parse(englishHtml).select("main p").map { it.text() }
        if (paragraphs.none { it.contains("each year", true) }) return emptyList()
        val windows = paragraphs.filter { it.contains("local time", true) && date.findAll(it).count() == 2 }
            .mapNotNull { window -> article(news, "<main><p>${org.jsoup.nodes.Entities.escape(window)}</p></main>") }
        val debutMonth = windows.firstOrNull()?.start?.monthValue ?: return emptyList()
        return (maxOf(currentYear, announcedYear)..maxOf(currentYear + 1, announcedYear)).flatMap { year ->
            windows.mapNotNull { event ->
                if (year == announcedYear && event.start.monthValue < debutMonth) null
                else event.copy(start = event.start.withYear(year), end = event.end.withYear(year))
            }
        }.distinctBy { it.start to it.end }
    }

    fun article(news: News, englishHtml: String): HomeEventArt? {
        val slug = news.link.substringAfterLast('/')
        // Location-specific announcements must not become a worldwide home background.
        if (listOf("city-safari", "safari-zone", "in-person", "ticket", "save-the-date").any { it in slug }) return null
        val doc = Jsoup.parse(englishHtml)
        // Only the introductory event window, never publication metadata or later bonus windows.
        val window = doc.select("main p").take(4).map { it.text() }.firstOrNull {
            it.contains("local time", true) && date.containsMatchIn(it)
        } ?: return null
        val matches = date.findAll(window).toList()
        if (matches.size !in 1..2) return null
        val year = (Regex("\\b20\\d{2}\\b").findAll(window).lastOrNull()?.value
            ?: Regex("(?:^|-)(20\\d{2})(?:-|$)").find(slug)?.groupValues?.get(1))?.toIntOrNull() ?: return null
        fun parsed(index: Int): LocalDate {
            val m = matches[index]
            val month = months.indexOfFirst { it.equals(m.groupValues[1], true) } + 1
            return LocalDate.of(m.groupValues[3].toIntOrNull() ?: year, month, m.groupValues[2].toInt())
        }
        return runCatching {
            var start = parsed(0)
            val end = parsed(matches.lastIndex)
            if (start > end && matches.first().groupValues[3].isBlank()) start = start.minusYears(1)
            require(!end.isBefore(start) && ChronoUnit.DAYS.between(start, end) <= 120)
            HomeEventArt(news.title, news.link, news.image, start, end, priority(slug))
        }.getOrNull()
    }
}

internal class HomeEventArtRepository(context: Context) {
    private val directory = File(context.filesDir, "home-event-art-v2")
    private fun cache(locale: String) = AtomicFile(File(directory, "$locale.json"))
    fun cached(locale: String): HomeEventArtCatalog? = runCatching {
        val json = cache(locale).openRead().bufferedReader().use { JSONObject(it.readText()) }
        val rows = json.getJSONArray("events")
        HomeEventArtCatalog((0 until rows.length()).map { i -> rows.getJSONObject(i).let {
            HomeEventArt(it.getString("title"), it.getString("link"), it.getString("image"),
                LocalDate.parse(it.getString("start")), LocalDate.parse(it.getString("end")), it.getInt("priority"))
        } }, if (json.optInt("schema") == 4) json.getLong("fetchedAt") else 0L)
    }.getOrNull()

    suspend fun refresh(locale: String): HomeEventArtCatalog = withContext(Dispatchers.IO) {
        val previous = cached(locale)?.events.orEmpty()
        var refreshed = false
        suspend fun source(isSeason: Boolean, load: suspend () -> List<HomeEventArt>): List<HomeEventArt> = try {
            load().also { refreshed = true }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            android.util.Log.w("HomeEventArt", "Keeping cached source (season=$isSeason)", error)
            previous.filter { ("/seasons/" in it.link) == isSeason }
        }
        val newsEvents = source(false) {
            val base = "https://pokemongo.com/$locale/news"
            val news = HomeEventArtParser.news(fetch(base), base)
            require(news.isNotEmpty()) { "Official news unavailable" }
            val permits = Semaphore(4)
            coroutineScope {
                news.map { item -> async {
                    permits.withPermit {
                        ensureActive()
                        // English gives one stable date grammar; title and art remain localized.
                        val html = fetch("https://pokemongo.com/en/news/" + item.link.substringAfterLast('/'))
                        ensureActive()
                        HomeEventArtParser.articles(item, html)
                    }
                } }.awaitAll().flatten()
            }
        }
        val seasonEvents = source(true) {
            val localized = fetch("https://pokemongo.com/$locale/seasons")
            ensureActive()
            val english = if (locale == "en") localized else fetch("https://pokemongo.com/en/seasons")
            listOf(requireNotNull(HomeEventArtParser.season(localized, english, locale)) { "Official season unavailable" })
        }
        check(refreshed) { "Official event sources unavailable" }
        val events = newsEvents + seasonEvents
        ensureActive()
        val result = HomeEventArtCatalog(events, System.currentTimeMillis())
        val json = JSONObject().put("schema", 4).put("fetchedAt", result.fetchedAt).put("events", JSONArray(events.map {
            JSONObject().put("title", it.title).put("link", it.link).put("image", it.image)
                .put("start", it.start.toString()).put("end", it.end.toString()).put("priority", it.priority)
        }))
        directory.mkdirs()
        val file = cache(locale)
        val stream = file.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
        result
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000; connection.readTimeout = 10_000
            check(connection.responseCode == 200)
            connection.inputStream.use { input ->
                val data = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(data.size() + read <= 3 * 1024 * 1024)
                    data.write(buffer, 0, read)
                }
                data.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
}