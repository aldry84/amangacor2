package com.AsianDrama

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

class AsianDrama : MainAPI() {
    override var mainUrl: String = runBlocking {
        AsianDramaProvider.getDomains()?.asiandrama ?: "https://asiandrama.com"
    }
    override var name = "AsianDrama"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"

    // PERBAIKAN: Struktur mainPage yang benar
    override val mainPage = mainPageOf(
        "$mainUrl/drama/ongoing" to "Ongoing Dramas",
        "$mainUrl/latest" to "Latest Releases",
        "$mainUrl/drama/chinese-drama" to "Chinese Dramas",
        "$mainUrl/drama/japanese-drama" to "Japanese Dramas",
        "$mainUrl/drama/korean-drama" to "Korean Dramas",
        "$mainUrl/movies" to "Movies",
        "$mainUrl/web-series" to "Web Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) {
            "${request.data}/page/$page"
        } else {
            request.data
        }
        
        val document = app.get(url).document
        val home = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title") ?: return null
        val title = titleElement.text()?.substringAfter("Download")?.trim() ?: return null
        val href = titleElement.select("a").attr("href") ?: return null
        
        val imgElement = this.selectFirst("img")
        val posterUrl = when {
            imgElement?.hasAttr("srcset") == true -> {
                imgElement.attr("srcset")
                    .split(",")
                    .map { it.trim() }
                    .mapNotNull { part ->
                        val urlAndSize = part.split(" ")
                        if (urlAndSize.size >= 2) {
                            val url = urlAndSize[0]
                            val size = urlAndSize[1].removeSuffix("w").toIntOrNull() ?: 0
                            url to size
                        } else null
                    }
                    .maxByOrNull { it.second }
                    ?.first
            }
            imgElement?.hasAttr("src") == true -> {
                imgElement.attr("src")
            }
            else -> null
        }

        // Tentukan jenis konten berdasarkan URL atau judul
        val type = when {
            href.contains("/movie") || title.contains("movie", true) -> TvType.Movie
            href.contains("/drama") || title.contains("drama", true) -> TvType.TvSeries
            else -> TvType.TvSeries // default ke TvSeries
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article").mapNotNull {
            it.toSearchResult()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        // Extract IMDb and TMDb IDs
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
            tmdbType?.contains("movie", true) == true -> TvType.Movie
            url.contains("/movie") -> TvType.Movie
            else -> TvType.TvSeries
        }

        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim() ?: document.selectFirst("h1.entry-title")?.text() ?: "Unknown Title"
        
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()

        // Fetch additional metadata from Cinemeta if available
        val typeset = if (tvType == TvType.TvSeries) "series" else "movie"
        val responseData = if (!tmdbId.isNullOrEmpty()) {
            try {
                val jsonResponse = app.get("$cinemeta_url/$typeset/tt${imdbId ?: tmdbId}.json").text
                if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                    Gson().fromJson(jsonResponse, ResponseData::class.java)
                } else null
            } catch (e: Exception) {
                Log.e("AsianDrama", "Error fetching Cinemeta data: ${e.message}")
                null
            }
        } else null

        var cast: List<String> = emptyList()
        var background: String = image
        var description: String? = descriptions

        if (responseData != null) {
            description = responseData.meta?.description ?: descriptions
            cast = responseData.meta?.cast ?: emptyList()
            background = responseData.meta?.background ?: image
        }

        // Extract download links
        val hrefs: List<String> = document.select("div.wp-block-button > a")
            .mapNotNull { linkElement ->
                val link = linkElement.attr("href")
                val actual = cinematickitloadBypass(link) ?: link
                try {
                    val page = app.get(actual).document
                    page.select("div.wp-block-button.movie_btn a")
                        .eachAttr("href")
                } catch (e: Exception) {
                    Log.e("AsianDrama", "Error loading link page: ${e.message}")
                    emptyList()
                }
            }.flatten()

        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")

        val recommendations = document.select("div.entry-related-inner-content article").mapNotNull {
            val recName = it.select("h3").text().substringAfter("Download").trim()
            val recHref = it.select("h3 a").attr("href")
            val recPosterUrl = it.select("img").attr("src")
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
            }
        }

        if (tvType == TvType.TvSeries) {
            val tvSeriesEpisodes = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

            val seasonBlocks = document.select("div.su-accordion h2")

            for (seasonHeader in seasonBlocks) {
                val seasonText = seasonHeader.text()
                if (seasonText.contains("ZIP", ignoreCase = true)) {
                    continue
                }

                val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                    .find(seasonText)
                val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

                var linksBlock = seasonHeader.nextElementSibling()
                if (linksBlock == null || linksBlock.select("div.wp-block-button").isEmpty()) {
                    linksBlock = seasonHeader.parent()?.selectFirst("div.su-spoiler-content")
                }

                val qualityLinks = linksBlock?.select("div.wp-block-button a")
                    ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                    ?.distinct() ?: emptyList()

                for (qualityPageLink in qualityLinks) {
                    try {
                        val rawqualityPageLink = if (qualityPageLink.contains("modpro")) {
                            qualityPageLink 
                        } else {
                            cinematickitloadBypass(qualityPageLink) ?: qualityPageLink
                        }
                        
                        val response = app.get(rawqualityPageLink)
                        val episodeDoc = response.document

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
                                    val key = season to epNo
                                    tvSeriesEpisodes.getOrPut(key) { mutableListOf() }.add(ephref)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EpisodeFetch", "Failed to load or parse $qualityPageLink: ${e.message}")
                    }
                }
            }

            val finalEpisodes = tvSeriesEpisodes.map { (seasonEpisode, links) ->
                val (season, epNo) = seasonEpisode
                val info = responseData?.meta?.videos?.find { it.season == season && it.episode == epNo }

                newEpisode(links.distinct().toJson()) {
                    this.name = info?.name ?: "Episode $epNo"
                    this.posterUrl = info?.thumbnail
                    this.season = season
                    this.episode = epNo
                    this.description = info?.overview
                }
            }.sortedWith(compareBy({ it.season }, { it.episode }))

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
                this.posterUrl = image
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.posterUrl = image
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = tryParseJson<List<String>>(data).orEmpty()
        if (links.isEmpty()) {
            Log.e("LoadLinks", "No links found in data: $data")
            return false
        }
        
        var success = false
        for (link in links) {
            try {
                val finalLink = when {
                    "safelink=" in link -> cinematickitBypass(link)
                    "unblockedgames" in link -> bypassHrefli(link)
                    "examzculture" in link -> bypassHrefli(link)
                    else -> link
                }

                if (finalLink != null) {
                    loadExtractor(finalLink, subtitleCallback, callback)
                    success = true
                }
            } catch (e: Exception) {
                Log.e("LoadLinks", "Failed to load link: $link, error: ${e.message}")
            }
        }

        return success
    }
}
