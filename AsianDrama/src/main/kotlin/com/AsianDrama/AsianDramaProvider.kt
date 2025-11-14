package com.AsianDrama

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document

// Model classes
data class DomainResponse(
    @JsonProperty("dramadrip") val dramadrip: String
)

data class CinemetaResponse(
    @JsonProperty("meta") val meta: CinemetaMeta?
)

data class CinemetaMeta(
    @JsonProperty("id") val id: String?,
    @JsonProperty("imdb_id") val imdbId: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("name") val name: String?,
    @JsonProperty("poster") val poster: String?,
    @JsonProperty("background") val background: String?,
    @JsonProperty("logo") val logo: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("releaseInfo") val releaseInfo: String?,
    @JsonProperty("runtime") val runtime: String?,
    @JsonProperty("cast") val cast: List<String>?,
    @JsonProperty("genre") val genre: List<String>?,
    @JsonProperty("imdbRating") val rating: String?,
    @JsonProperty("trailer") val trailer: String?
)

data class AsianDramaLinkData(
    val title: String? = null,
    val year: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val rawLinks: List<String> = emptyList()
)

class AsianDramaProvider : MainAPI() {
    override var mainUrl = "https://dramadrip.com"
    override var name = "AsianDrama"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)

    // Domain management
    companion object {
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/your-repo/domains.json"
        var cachedDomain: String? = null

        suspend fun getCurrentDomain(): String {
            if (cachedDomain == null) {
                try {
                    val response = app.get(DOMAINS_URL).parsedSafe<DomainResponse>()
                    cachedDomain = response?.dramadrip
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return cachedDomain ?: "https://dramadrip.com"
        }
    }

    override val mainPage = mainPageOf(
        "drama/ongoing" to "Ongoing Dramas",
        "latest" to "Latest Releases",
        "drama/chinese-drama" to "Chinese Dramas",
        "drama/japanese-drama" to "Japanese Dramas",
        "drama/korean-drama" to "Korean Dramas",
        "movies" to "Movies",
        "web-series" to "Web Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        mainUrl = getCurrentDomain()
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("article").mapNotNull { it.toAsianDramaSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toAsianDramaSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = this.selectFirst("h2.entry-title > a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        mainUrl = getCurrentDomain()
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article").mapNotNull {
            it.toAsianDramaSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        mainUrl = getCurrentDomain()
        val document = app.get(url).document

        // Extract basic info from DramaDrip
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim() ?: throw ErrorLoadingException("No title found")
        
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        val description = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }

        // Extract TMDB/IMDB IDs for Cinemeta
        val (tmdbId, imdbId) = extractIdsFromDocument(document)

        // Get enhanced metadata from Cinemeta
        val cinemetaData = getCinemetaMetadata(tmdbId, if (url.contains("/movie")) "movie" else "series")

        // Check if it's a series by looking for episodes
        val isSeries = document.select("div.su-accordion h2").any { 
            it.text().contains("Season", ignoreCase = true) && !it.text().contains("ZIP", ignoreCase = true) 
        }

        if (isSeries) {
            // TV Series - Extract episodes
            val episodes = extractEpisodesFromDocument(document)
            
            return newTvSeriesLoadResponse(
                cinemetaData?.name ?: title,
                url, 
                TvType.AsianDrama, 
                episodes
            ) {
                this.posterUrl = cinemetaData?.poster ?: poster
                this.backgroundPosterUrl = cinemetaData?.background
                this.year = year
                this.plot = cinemetaData?.description ?: description
                this.tags = tags
                this.score = cinemetaData?.rating?.toFloatOrNull()?.let { Score.from10(it) }
                // Convert cast list to List<Actor>
                val actors = cinemetaData?.cast?.map { Actor(it) } 
                addActors(actors)
                addTrailer(cinemetaData?.trailer)
                addTMDbId(tmdbId?.toString())
                addImdbId(imdbId)
            }
        } else {
            // Movie - Extract streaming links
            val streamingLinks = extractStreamingLinksFromDocument(document)
            
            return newMovieLoadResponse(
                cinemetaData?.name ?: title,
                url,
                TvType.Movie,
                AsianDramaLinkData(
                    title = title,
                    year = year,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    rawLinks = streamingLinks
                ).toJson()
            ) {
                this.posterUrl = cinemetaData?.poster ?: poster
                this.backgroundPosterUrl = cinemetaData?.background
                this.year = year
                this.plot = cinemetaData?.description ?: description
                this.tags = tags
                this.score = cinemetaData?.rating?.toFloatOrNull()?.let { Score.from10(it) }
                val actors = cinemetaData?.cast?.map { Actor(it) }
                addActors(actors)
                addTrailer(cinemetaData?.trailer)
                addTMDbId(tmdbId?.toString())
                addImdbId(imdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkData = tryParseJson<AsianDramaLinkData>(data) ?: return false
        
        // Process all Asian-optimized extractors
        linkData.rawLinks.forEach { link ->
            loadExtractor(link, mainUrl, subtitleCallback, callback)
        }

        return true
    }

    // ==================== UTILITY FUNCTIONS ====================

    private suspend fun getCinemetaMetadata(tmdbId: Int?, type: String): CinemetaMeta? {
        if (tmdbId == null) return null
        try {
            val response = app.get("https://v3-cinemeta.strem.io/meta/$type/$tmdbId.json")
            return response.parsedSafe<CinemetaResponse>()?.meta
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun extractIdsFromDocument(document: Document): Pair<Int?, String?> {
        var tmdbId: Int? = null
        var imdbId: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }
            if (tmdbId == null && "themoviedb.org" in text) {
                tmdbId = Regex("/(movie|tv)/(\\d+)").find(text)?.groupValues?.get(2)?.toIntOrNull()
            }
        }

        return Pair(tmdbId, imdbId)
    }

    private fun extractStreamingLinksFromDocument(document: Document): List<String> {
        val links = mutableListOf<String>()
        
        document.select("div.wp-block-button > a").forEach { link ->
            val href = link.attr("href")
            if (href.isNotBlank() && !href.contains("javascript")) {
                links.add(href)
            }
        }
        
        return links
    }

    private fun extractEpisodesFromDocument(document: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        
        document.select("div.su-accordion h2").forEach { seasonHeader ->
            val seasonText = seasonHeader.text()
            if (!seasonText.contains("ZIP", ignoreCase = true)) {
                val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                    .find(seasonText)
                val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

                // Extract episode links from this season
                val episodeElements = seasonHeader.nextElementSibling()
                    ?.select("div.wp-block-button a")
                    ?: emptyList()

                episodeElements.forEachIndexed { epIndex, episodeLink ->
                    val href = episodeLink.attr("href")
                    if (href.isNotBlank()) {
                        episodes.add(
                            newEpisode(href) {
                                this.name = "Episode ${epIndex + 1}"
                                this.season = season
                                this.episode = epIndex + 1
                            }
                        )
                    }
                }
            }
        }
        
        return episodes
    }
}
