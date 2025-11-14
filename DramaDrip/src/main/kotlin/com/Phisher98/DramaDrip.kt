package com.Phisher98

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

class DramaDrip : MainAPI() {
    override var mainUrl: String = runBlocking {
        DramaDripProvider.getAvailableDomain()
    }
    override var name = "DramaDrip"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)
    private val cinemeta_url = "https://v3-cinemeta.strem.io/meta"

    override val mainPage = mainPageOf(
        "drama/ongoing" to "Ongoing Dramas",
        "latest" to "Latest Releases",
        "drama/chinese-drama" to "Chinese Dramas",
        "drama/japanese-drama" to "Japanese Dramas",
        "drama/korean-drama" to "Korean Dramas",
        "movies" to "Movies",
        "web-series" to "Web Series",
    )

    // Enhanced error handling for main page
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val document = app.get("$mainUrl/${request.data}/page/$page").document
            val home = document.select("article").mapNotNull { it.toSearchResult() }

            newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = false
                ),
                hasNext = true
            )
        } catch (e: Exception) {
            Log.e("MainPage", "Failed to load main page ${request.name}: ${e.message}")
            newHomePageResponse(emptyList(), hasNext = false)
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        return try {
            val title =
                this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
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
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("SearchResult", "Failed to parse search result: ${e.message}")
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val document = app.get("$mainUrl/?s=$query").document
            document.select("article").mapNotNull {
                it.toSearchResult()
            }
        } catch (e: Exception) {
            Log.e("Search", "Search failed for query '$query': ${e.message}")
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        return try {
            loadWithFallback(url)
        } catch (e: Exception) {
            Log.e("Load", "Critical error loading $url: ${e.message}")
            // Return a basic error response
            newMovieLoadResponse("Error", url, TvType.Movie, emptyList()) {
                this.plot = "Failed to load content: ${e.message}"
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun loadWithFallback(url: String): LoadResponse {
        val document = try {
            app.get(url).document
        } catch (e: Exception) {
            throw Exception("Failed to fetch page: ${e.message}")
        }

        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        // Extract IDs from the page
        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }

            if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                    tmdbType = match.groupValues[1] // movie or tv
                    tmdbId = match.groupValues[2]   // numeric ID
                }
            }
        }

        val tvType = when {
            tmdbType?.equals("movie", ignoreCase = true) == true -> TvType.Movie
            tmdbType?.equals("tv", ignoreCase = true) == true -> TvType.TvSeries
            else -> TvType.TvSeries // default to TvSeries for dramas
        }

        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim().toString()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()

        // ========== ENHANCED TMDb INTEGRATION WITH FALLBACK ==========
        val tmdbData = if (!tmdbId.isNullOrEmpty() && !tmdbType.isNullOrEmpty()) {
            try {
                fetchTMDbData(tmdbId!!, tmdbType!!)
            } catch (e: Exception) {
                Log.w("TMDb", "TMDb fetch failed, using fallback data: ${e.message}")
                null
            }
        } else null

        // Use TMDb data if available, otherwise use existing sources with fallback
        val finalTitle = tmdbData?.title ?: tmdbData?.name ?: title.ifEmpty { "Unknown Title" }
        val finalDescription = tmdbData?.overview ?: descriptions ?: "No description available"
        val finalYear = tmdbData?.release_date?.substringBefore("-")?.toIntOrNull() 
            ?: tmdbData?.first_air_date?.substringBefore("-")?.toIntOrNull() 
            ?: year

        // Get high quality images from TMDb with fallback
        val posterUrl = getTMDbImageUrl(tmdbData?.poster_path) ?: image
        val background = getTMDbImageUrl(tmdbData?.backdrop_path, "w1280") ?: image

        // Get cast from TMDb (limited to 10 main actors) with fallback
        val cast = tmdbData?.credits?.cast
            ?.sortedBy { it.order ?: 999 } // Sort by appearance order
            ?.take(10)
            ?.mapNotNull { it.name } 
            ?: emptyList()

        // Get genres from TMDb with fallback
        val genres = tmdbData?.genres?.mapNotNull { it.name } ?: emptyList()
        val allTags = (tags + genres).distinct()

        // Get trailer from TMDb
        val trailer = tmdbData?.videos?.results
            ?.find { it.site == "YouTube" && (it.type == "Trailer" || it.type == "Teaser") }
            ?.key
        val trailerUrl = if (!trailer.isNullOrEmpty()) "https://www.youtube.com/watch?v=$trailer" else null

        val hrefs: List<String> = try {
            document.select("div.wp-block-button > a")
                .mapNotNull { linkElement ->
                    val link = linkElement.attr("href")
                    val actual = cinematickitloadBypass(link) ?: return@mapNotNull null
                    val page = app.get(actual).document
                    page.select("div.wp-block-button.movie_btn a")
                        .eachAttr("href")
                }.flatten()
        } catch (e: Exception) {
            Log.e("Links", "Failed to extract hrefs: ${e.message}")
            emptyList()
        }

        val recommendations = try {
            document.select("div.entry-related-inner-content article").mapNotNull {
                val recName = it.select("h3").text().substringAfter("Download")
                val recHref = it.select("h3 a").attr("href")
                val recPosterUrl = it.select("img").attr("src")
                newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                    this.posterUrl = recPosterUrl
                }
            }
        } catch (e: Exception) {
            Log.e("Recommendations", "Failed to parse recommendations: ${e.message}")
            emptyList()
        }

        if (tvType == TvType.TvSeries) {
            val tvSeriesEpisodes = mutableMapOf<Pair<Int, Int>, MutableList<String>>()

            val seasonBlocks = document.select("div.su-accordion h2")

            for (seasonHeader in seasonBlocks) {
                try {
                    val seasonText = seasonHeader.text()
                    if (seasonText.contains("ZIP", ignoreCase = true)) {
                        Log.d("Skip", "Skipping ZIP season: $seasonText")
                    } else {
                        val seasonMatch = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
                            .find(seasonText)
                        val season = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

                        if (season != null) {
                            var linksBlock = seasonHeader.nextElementSibling()
                            if (linksBlock == null || linksBlock.select("div.wp-block-button")
                                    .isEmpty()
                            ) {
                                linksBlock = seasonHeader.parent()?.selectFirst("div.wp-block-button")
                                    ?: linksBlock
                            }

                            val qualityLinks = linksBlock?.select("div.wp-block-button a")
                                ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
                                ?.distinct() ?: emptyList()

                            for (qualityPageLink in qualityLinks) {
                                try {
                                    val rawqualityPageLink = if (qualityPageLink.contains("modpro")) qualityPageLink else cinematickitloadBypass(qualityPageLink) ?: ""
                                    val response = app.get(rawqualityPageLink)
                                    val episodeDoc = response.document

                                    val episodeButtons =
                                        episodeDoc.select("a").filter { element: Element ->
                                            element.text()
                                                .matches(Regex("""(?i)(Episode|Ep|E)?\s*0*\d+"""))
                                        }

                                    for (btn in episodeButtons) {
                                        val ephref = btn.attr("href")
                                        val epText = btn.text()

                                        if (ephref.isNotBlank()) {
                                            val epNo = Regex(
                                                """(?:Episode|Ep|E)?\s*0*([0-9]+)""",
                                                RegexOption.IGNORE_CASE
                                            )
                                                .find(epText)?.groupValues?.getOrNull(1)?.toIntOrNull()

                                            if (epNo != null) {
                                                val key = season to epNo
                                                tvSeriesEpisodes.getOrPut(key) { mutableListOf() }
                                                    .add(ephref)
                                            } else {
                                                Log.w(
                                                    "EpisodeFetch",
                                                    "Could not extract episode number from text: '$epText'"
                                                )
                                            }
                                        } else {
                                            Log.w(
                                                "EpisodeFetch",
                                                "Empty href for episode button with text: '$epText'"
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("EpisodeFetch", "Failed to load or parse $qualityPageLink: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SeasonParse", "Failed to parse season: ${e.message}")
                }
            }

            val finalEpisodes = tvSeriesEpisodes.map { (seasonEpisode, links) ->
                val (season, epNo) = seasonEpisode
                
                // Get episode data from TMDb if available
                val episodeData = if (!tmdbId.isNullOrEmpty()) {
                    try {
                        fetchTMDbEpisode(tmdbId!!, season, epNo)
                    } catch (e: Exception) {
                        Log.w("TMDbEpisode", "Failed to fetch TMDb episode data: ${e.message}")
                        null
                    }
                } else null

                newEpisode(links.distinct().toJson()) {
                    this.name = episodeData?.name ?: "Episode $epNo"
                    this.posterUrl = getTMDbImageUrl(episodeData?.still_path)
                    this.season = season
                    this.episode = epNo
                    this.description = episodeData?.overview
                }
            }

            return newTvSeriesLoadResponse(finalTitle, url, TvType.TvSeries, finalEpisodes) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = background
                this.year = finalYear
                this.plot = finalDescription
                this.tags = allTags
                this.recommendations = recommendations
                addTrailer(trailerUrl)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        } else {
            return newMovieLoadResponse(finalTitle, url, TvType.Movie, hrefs) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = background
                this.year = finalYear
                this.plot = finalDescription
                this.tags = allTags
                this.recommendations = recommendations
                addTrailer(trailerUrl)
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
        return try {
            val links = tryParseJson<List<String>>(data).orEmpty()
            if (links.isEmpty()) {
                Log.e("LoadLinks", "No links found in data: $data")
                return false
            }
            
            var successCount = 0
            for (link in links) {
                try {
                    val finalLink = when {
                        "safelink=" in link -> cinematickitBypass(link)
                        "unblockedgames" in link -> bypassHrefli(link)
                        "examzculture" in link -> bypassHrefli(link)
                        else -> link
                    }

                    if (finalLink != null) {
                        if (loadExtractor(finalLink, subtitleCallback, callback)) {
                            successCount++
                        }
                    } else {
                        Log.w("LoadLinks", "Bypass returned null for link: $link")
                    }
                } catch (e: Exception) {
                    Log.e("LoadLinks", "Failed to load link: $link - ${e.message}")
                }
            }
            
            successCount > 0
        } catch (e: Exception) {
            Log.e("LoadLinks", "Critical error in loadLinks: ${e.message}")
            false
        }
    }
}
