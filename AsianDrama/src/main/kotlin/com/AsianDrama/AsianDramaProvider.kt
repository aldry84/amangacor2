package com.AsianDrama

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

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
                    val response = app.get(DOMAINS_URL).parsedSafe<DramaModels.DomainResponse>()
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
        val (tmdbId, imdbId) = DramaUtils.extractIdsFromDocument(document)

        // Get enhanced metadata from Cinemeta
        val cinemetaData = DramaUtils.getCinemetaMetadata(tmdbId, if (url.contains("/movie")) "movie" else "series")

        // Check if it's a series by looking for episodes
        val isSeries = document.select("div.su-accordion h2").any { 
            it.text().contains("Season", ignoreCase = true) && !it.text().contains("ZIP", ignoreCase = true) 
        }

        if (isSeries) {
            // TV Series - Extract episodes
            val episodes = DramaUtils.extractEpisodesFromDocument(document, url)
            
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
                addActors(cinemetaData?.cast?.map { ActorData(Actor(it)) } ?: emptyList())
                addTrailer(cinemetaData?.trailer)
                addTMDbId(tmdbId?.toString())
                addImdbId(imdbId)
            }
        } else {
            // Movie - Extract streaming links
            val streamingLinks = DramaUtils.extractStreamingLinksFromDocument(document)
            
            return newMovieLoadResponse(
                cinemetaData?.name ?: title,
                url,
                TvType.Movie,
                DramaModels.AsianDramaLinkData(
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
                addActors(cinemetaData?.cast?.map { ActorData(Actor(it)) } ?: emptyList())
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
        val linkData = tryParseJson<DramaModels.AsianDramaLinkData>(data) ?: return false
        
        // Process all Asian-optimized extractors in parallel
        runAllAsync(
            { 
                AsianDramaExtractors.IdlixExtractor().getUrl(
                    linkData.rawLinks.firstOrNull() ?: return@runAllAsync,
                    mainUrl,
                    subtitleCallback,
                    callback
                ) 
            },
            { 
                AsianDramaExtractors.MappleExtractor().getUrl(
                    linkData.rawLinks.firstOrNull() ?: return@runAllAsync, 
                    mainUrl,
                    subtitleCallback,
                    callback
                ) 
            },
            { 
                AsianDramaExtractors.WyzieExtractor().getUrl(
                    "", // Wyzie doesn't need URL for subtitle extraction
                    mainUrl,
                    subtitleCallback,
                    callback
                ) 
            },
            { 
                AsianDramaExtractors.GomoviesExtractor().getUrl(
                    linkData.rawLinks.firstOrNull() ?: return@runAllAsync,
                    mainUrl,
                    subtitleCallback,
                    callback
                ) 
            }
        )

        return true
    }
}
