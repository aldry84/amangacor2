package com.adimoviemaze

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import java.util.Base64

class Adimoviemaze : MainAPI() {
    override var mainUrl = "https://watch32.sx"  // Changed to .sx based on your request; verify if structure matches .co
    override var name = "AdiMovieMaze"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "en"

    companion object {
        fun getType(t: String): TvType {
            return when {
                t.contains("/tv/") -> TvType.TvSeries
                else -> TvType.Movie
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val homeSections = ArrayList<HomePageList>()

        // Featured or Popular Section (adapt selector if different)
        val featured = document.select("div#popular-movies > div.row > div.item").mapNotNull {
            it.toSearchResult()
        }
        if (featured.isNotEmpty()) {
            homeSections.add(HomePageList("Popular Movies", featured, isHorizontalImages = true))
        }

        // Latest Movies Section
        val latest = document.select("div#latest-movies > div.row > div.item").mapNotNull {
            it.toSearchResult()
        }
        if (latest.isNotEmpty()) {
            homeSections.add(HomePageList("Latest Releases", latest, isHorizontalImages = true))
        }

        // Add more sections if the site has them, e.g., Trending, Genres
        // Example: val trending = document.select("selector-for-trending").mapNotNull { ... }

        return HomePageResponse(homeSections)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        val quality = parseQuality(this.selectFirst("div.quality")?.text())
        return newMovieSearchResponse(title, href, getType(href)) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search?q=$query"
        val document = app.get(searchUrl).document
        return document.select("div.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val tags = document.select("div.genres a").map { it.text() }
        val year = document.selectFirst("div.year")?.text()?.toIntOrNull()
        val plot = document.selectFirst("div.plot")?.text()?.trim()
        val trailer = fixUrlNull(document.selectFirst("a.trailer")?.attr("href"))

        val recommendations = document.select("div.related div.item").mapNotNull { it.toSearchResult() }

        val actors = document.select("div.cast a").map {
            Actor(it.text(), fixUrlNull(it.selectFirst("img")?.attr("src")))
        }

        val isTvSeries = url.contains("/tv/")

        return if (isTvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.season").forEach { season ->
                val seasonNum = season.selectFirst("h3")?.text()?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                season.select("div.episode a").forEachIndexed { index, ep ->
                    val epHref = fixUrl(ep.attr("href"))
                    val epName = ep.text().trim()
                    val epNum = index + 1
                    episodes.add(newEpisode(epHref) {
                        this.name = epName
                        this.season = seasonNum
                        this.episode = epNum
                    })
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = plot
                this.tags = tags
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Find server links or embeds
        document.select("div.server-list a, iframe[src]").apmap { element ->
            val link = fixUrl(element.attr("href") ?: element.attr("src"))
            if (link.contains("embed") || link.contains("player")) {
                // Load extractor for embedded players
                loadExtractor(link, data, subtitleCallback, callback)
            } else {
                // If direct server, fetch and decode if needed
                val res = app.get(link).text
                // Look for obfuscated JS
                val jsCode = Regex("""eval\('(.*?)'\)""").find(res)?.groupValues?.get(1)
                if (jsCode != null) {
                    val decoded = String(Base64.getDecoder().decode(jsCode))
                    val videoUrl = Regex("""file:\s*"([^"]+)"""").find(decoded)?.groupValues?.get(1)
                    if (videoUrl != null) {
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                this.name,
                                videoUrl,
                                "",
                                getQualityFromName("HD"),  // Adapt quality
                                isM3u8 = videoUrl.contains(".m3u8")
                            )
                        )
                    }
                }
            }
        }

        return true
    }
}
