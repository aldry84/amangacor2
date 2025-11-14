// AsianDrama/src/main/kotlin/com/AsianDrama/AsianDrama.kt
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
import com.lagradost.cloudstream3.utils.SubtitleFile
import org.jsoup.nodes.Element

class AsianDrama : MainAPI() {
    override var mainUrl = "https://dramadrip.com"
    override var name = "AsianDrama" 
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)

    // Domain management similar to DramaDrip
    companion object {
        private const val DOMAINS_URL = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }

        data class Domains(
            @JsonProperty("dramadrip") val dramadrip: String,
        )
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
        val document = app.get("$mainUrl/${request.data}/page/$page").document
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
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = this.select("h2.entry-title > a").attr("href")
        val imgElement = this.selectFirst("img")
        val srcset = imgElement?.attr("srcset")

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
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        val results = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Extract metadata from DramaDrip page
        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }

            if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                    tmdbType = match.groupValues[1]
                    tmdbId = match.groupValues[2]
                }
            }
        }

        // Determine type
        val tvType = when {
            tmdbType?.contains("movie", ignoreCase = true) == true -> TvType.Movie
            else -> TvType.TvSeries
        }

        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim().toString()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val description = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        
        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")

        val recommendations = document.select("div.entry-related-inner-content article").mapNotNull {
            val recName = it.select("h3").text().substringAfter("Download")
            val recHref = it.select("h3 a").attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        // Prepare data for SoraStream extractors
        val streamData = StreamData(
            tmdbId = tmdbId?.toIntOrNull(),
            imdbId = imdbId,
            title = title,
            year = year,
            type = if (tvType == TvType.Movie) "movie" else "tv"
        )

        if (tvType == TvType.TvSeries) {
            // Parse episodes from DramaDrip
            val episodes = parseDramaDripEpisodes(document, streamData)
            
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = image
                this.backgroundPosterUrl = image
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, streamData.toJson()) {
                this.posterUrl = image
                this.backgroundPosterUrl = image
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        }
    }

    private suspend fun parseDramaDripEpisodes(document: org.jsoup.nodes.Document, streamData: StreamData): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val seasonBlocks = document.select("div.su-accordion h2")

        for (seasonHeader in seasonBlocks) {
            val seasonText = seasonHeader.text()
            if (seasonText.contains("ZIP", ignoreCase = true)) continue

            val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                .find(seasonText)
            val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            val linksBlock = seasonHeader.nextElementSibling()
            val qualityLinks = linksBlock?.select("div.wp-block-button a")
                ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                ?.distinct() ?: emptyList()

            for (qualityPageLink in qualityLinks) {
                try {
                    val episodeDoc = app.get(qualityPageLink).document
                    val episodeButtons = episodeDoc.select("a").filter { element ->
                        element.text().matches(Regex("""(?i)(Episode|Ep|E)?\s*0*\d+"""))
                    }

                    for (btn in episodeButtons) {
                        val epText = btn.text()
                        val epNo = Regex("""(?:Episode|Ep|E)?\s*0*([0-9]+)""", RegexOption.IGNORE_CASE)
                            .find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

                        if (epNo != null) {
                            val episodeData = streamData.copy(
                                season = season,
                                episode = epNo
                            )
                            episodes.add(
                                newEpisode(episodeData.toJson()) {
                                    this.name = "Episode $epNo"
                                    this.season = season
                                    this.episode = epNo
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    // Continue with next quality link
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
        val streamData = tryParseJson<StreamData>(data) ?: return false
        
        // Use SoraStream extractors
        AsianDramaExtractor.invokeAllExtractors(streamData, subtitleCallback, callback)
        
        return true
    }

    data class StreamData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val title: String? = null,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val type: String? = null
    )
}
