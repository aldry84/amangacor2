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
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
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
        // Mengikuti pola dari DramaDrip.kt - lebih sederhana
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = this.select("h2.entry-title > a").attr("href")
        val imgElement = this.selectFirst("img")
        val srcset = imgElement?.attr("srcset")

        // Mengambil highest resolution image seperti di DramaDrip.kt
        val highestResUrl = srcset
            ?.split(",")
            ?.map { it.trim() }
            ?.mapNotNull {
                val parts = it.split(" ")
                if (parts.size == 2) parts[0] to parts[1].removeSuffix("w").toIntOrNull() else null
            }
            ?.maxByOrNull { it.second ?: 0 }
            ?.first

        val posterUrl = highestResUrl ?: imgElement?.attr("src")
        
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

        // Extract IDs seperti di DramaDrip.kt
        var imdbId: String? = null
        var tmdbId: Int? = null
        var tmdbType: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }

            if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                    tmdbType = match.groupValues[1]
                    tmdbId = match.groupValues[2].toIntOrNull()
                }
            }
        }

        val tvType = when {
            (tmdbType?.contains("Movie", ignoreCase = true) == true) -> TvType.Movie
            else -> TvType.TvSeries
        }

        // Extract basic info seperti di DramaDrip.kt
        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim() ?: "Unknown Title"
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()

        val typeset = if (tvType == TvType.TvSeries) "series" else "movie"
        val responseData = if (tmdbId != null) {
            getCinemetaMetadata(tmdbId, typeset)
        } else null

        var cast: List<String> = emptyList()
        var background: String = image
        var description: String? = descriptions
        
        if (responseData != null) {
            description = responseData.description ?: descriptions
            cast = responseData.cast ?: emptyList()
            background = responseData.background ?: image
        }

        // Extract streaming links seperti di DramaDrip.kt
        val hrefs = document.select("div.wp-block-button > a")
            .mapNotNull { linkElement ->
                val link = linkElement.attr("href")
                // Di DramaDrip.kt ada cinematickitloadBypass, kita skip dulu
                link // Untuk sekarang langsung return link tanpa bypass
            }

        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")

        // Extract recommendations
        val recommendations = document.select("div.entry-related-inner-content article").mapNotNull {
            val recName = it.select("h3").text().substringAfter("Download")
            val recHref = it.select("h3 a").attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        return if (tvType == TvType.TvSeries) {
            // TV Series - Extract episodes seperti di DramaDrip.kt
            val episodes = extractEpisodesFromDocument(document)
            
            newTvSeriesLoadResponse(
                title,
                url,
                TvType.AsianDrama,
                episodes
            ) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description ?: "No description available"
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast.map { Actor(it) })
                addImdbId(imdbId)
                addTMDbId(tmdbId?.toString())
            }
        } else {
            // Movie
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                AsianDramaLinkData(
                    title = title,
                    year = year,
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    rawLinks = hrefs
                ).toJson()
            ) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description ?: "No description available"
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast.map { Actor(it) })
                addImdbId(imdbId)
                addTMDbId(tmdbId?.toString())
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
        
        // Process links dengan extractor yang sudah diregistrasi
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

    // PERBAIKAN: Tambahkan keyword 'suspend' di sini
    private suspend fun extractEpisodesFromDocument(document: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seasonBlocks = document.select("div.su-accordion h2")

        for (seasonHeader in seasonBlocks) {
            val seasonText = seasonHeader.text()
            if (seasonText.contains("ZIP", ignoreCase = true)) {
                continue // Skip ZIP seasons
            } else {
                val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                    .find(seasonText)
                val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

                // Try to get the links block
                var linksBlock = seasonHeader.nextElementSibling()
                if (linksBlock == null || linksBlock.select("div.wp-block-button").isEmpty()) {
                    linksBlock = seasonHeader.parent()?.selectFirst("div.wp-block-button") ?: linksBlock
                }

                val qualityLinks = linksBlock?.select("div.wp-block-button a")
                    ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                    ?.distinct() ?: emptyList()

                for (qualityPageLink in qualityLinks) {
                    try {
                        // PERBAIKAN: app.get() adalah suspend function, jadi ini sudah benar
                        val episodeDoc = app.get(qualityPageLink).document
                        val episodeButtons = episodeDoc.select("a").filter { element ->
                            element.text().matches(Regex("""(?i)(Episode|Ep|E)?\s*0*\d+"""))
                        }

                        for (btn in episodeButtons) {
                            val ephref = btn.attr("href")
                            val epText = btn.text()

                            if (ephref.isNotBlank()) {
                                val epNo = Regex("""(?:Episode|Ep|E)?\s*0*([0-9]+)""", RegexOption.IGNORE_CASE)
                                    .find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

                                if (epNo != null) {
                                    episodes.add(
                                        newEpisode(ephref) {
                                            this.name = "Episode $epNo"
                                            this.season = season
                                            this.episode = epNo
                                        }
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Continue dengan episode berikutnya jika ada error
                        continue
                    }
                }
            }
        }

        return episodes
    }
}
