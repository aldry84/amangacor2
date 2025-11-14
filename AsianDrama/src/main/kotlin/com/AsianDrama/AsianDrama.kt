package com.AsianDrama

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class AsianDrama : MainAPI() {
    override var mainUrl = "https://dramadrip.com"
    override var name = "AsianDrama" 
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "Latest Releases",
        "drama/ongoing" to "Ongoing Dramas",
        "drama/korean-drama" to "Korean Dramas",
        "drama/chinese-drama" to "Chinese Dramas", 
        "drama/japanese-drama" to "Japanese Dramas",
        "movies" to "Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (request.data.isEmpty()) "$mainUrl/page/$page" else "$mainUrl/${request.data}/page/$page"
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title, h3.entry-title") ?: return null
        val title = titleElement.text().replace("Download", "").trim()
        if (title.isBlank()) return null

        val href = titleElement.select("a").attr("href") ?: return null
        
        // Get poster image
        val imgElement = this.selectFirst("img")
        val posterUrl = when {
            imgElement?.attr("src")?.isNotBlank() == true -> imgElement.attr("src")
            imgElement?.attr("data-src")?.isNotBlank() == true -> imgElement.attr("data-src")
            else -> null
        }

        // Determine type from URL or content
        val type = when {
            href.contains("/movie/") -> TvType.Movie
            else -> TvType.TvSeries
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=${query}").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Extract basic metadata
        val title = document.selectFirst("h1.entry-title, h2.wp-block-heading")?.text()
            ?.replace("Download", "")?.trim() ?: "Unknown Title"
        
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img.wp-post-image, img.attachment-full")?.attr("src")
        
        val description = document.selectFirst("div.entry-content, div.content-section")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        // Extract year from title or content
        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: document.selectFirst("time.entry-date")?.text()?.takeLast(4)?.toIntOrNull()

        // Check if it's a movie or series by looking for episodes
        val hasEpisodes = document.select("div.su-accordion, .episode-list, .season-section").isNotEmpty()
        val type = if (hasEpisodes) TvType.TvSeries else TvType.Movie

        // Extract TMDB/IMDB IDs if available
        var tmdbId: Int? = null
        var imdbId: String? = null
        
        document.select("a").forEach { link ->
            val href = link.attr("href")
            when {
                href.contains("themoviedb.org/movie/") -> {
                    tmdbId = Regex("""/movie/(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                }
                href.contains("themoviedb.org/tv/") -> {
                    tmdbId = Regex("""/tv/(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()
                }
                href.contains("imdb.com/title/") -> {
                    imdbId = Regex("""/title/(tt\d+)""").find(href)?.groupValues?.get(1)
                }
            }
        }

        val trailer = document.selectFirst("iframe[src*='youtube'], iframe[src*='youtu.be']")?.attr("src")

        if (type == TvType.TvSeries) {
            val episodes = parseEpisodes(document, tmdbId, imdbId, title)
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.year = year
                this.plot = description
                addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, MovieStreamData(tmdbId, imdbId, title).toJson()) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.year = year
                this.plot = description
                addTrailer(trailer)
            }
        }
    }

    private suspend fun parseEpisodes(document: org.jsoup.nodes.Document, tmdbId: Int?, imdbId: String?, title: String): List<Episode> {
        val episodes = mutableListOf<Episode>()

        // Method 1: Accordion style episodes (common in DramaDrip)
        document.select("div.su-accordion, .season-section").forEach { seasonBlock ->
            val seasonHeader = seasonBlock.selectFirst("h2, h3, .season-title")?.text() ?: ""
            val seasonNumber = Regex("""(?i)season\s*(\d+)""").find(seasonHeader)?.groupValues?.get(1)?.toIntOrNull() ?: 1
            
            seasonBlock.select("a").forEach { episodeLink ->
                val episodeText = episodeLink.text()
                val episodeNumber = Regex("""(?i)episode\s*(\d+)""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                    ?: Regex("""\b(\d+)\b""").find(episodeText)?.groupValues?.get(1)?.toIntOrNull()
                
                if (episodeNumber != null) {
                    val episodeUrl = episodeLink.attr("href")
                    if (episodeUrl.isNotBlank()) {
                        episodes.add(
                            newEpisode(EpisodeStreamData(tmdbId, imdbId, title, seasonNumber, episodeNumber, episodeUrl).toJson()) {
                                this.name = "Episode $episodeNumber"
                                this.season = seasonNumber
                                this.episode = episodeNumber
                            }
                        )
                    }
                }
            }
        }

        // Method 2: Direct episode links
        if (episodes.isEmpty()) {
            document.select("a").forEach { link ->
                val linkText = link.text()
                if (linkText.contains("episode", true) || Regex("""\b(ep|episode)\s*\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(linkText)) {
                    val episodeNumber = Regex("""(\d+)""").find(linkText)?.groupValues?.get(1)?.toIntOrNull()
                    if (episodeNumber != null) {
                        val episodeUrl = link.attr("href")
                        episodes.add(
                            newEpisode(EpisodeStreamData(tmdbId, imdbId, title, 1, episodeNumber, episodeUrl).toJson()) {
                                this.name = "Episode $episodeNumber"
                                this.season = 1
                                this.episode = episodeNumber
                            }
                        )
                    }
                }
            }
        }

        return episodes.distinctBy { "${it.season}-${it.episode}" }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            // Try to parse as movie first
            val movieData = tryParseJson<MovieStreamData>(data)
            if (movieData != null) {
                AsianDramaExtractor.extractForMovie(movieData, subtitleCallback, callback)
                return true
            }

            // Try to parse as episode
            val episodeData = tryParseJson<EpisodeStreamData>(data)
            if (episodeData != null) {
                AsianDramaExtractor.extractForEpisode(episodeData, subtitleCallback, callback)
                return true
            }

            false
        } catch (e: Exception) {
            false
        }
    }

    data class MovieStreamData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val title: String? = null
    )

    data class EpisodeStreamData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val title: String? = null,
        val season: Int = 1,
        val episode: Int = 1,
        val episodeUrl: String? = null
    )
}
