package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.SubtitleFile
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element
import com.lagradost.api.Log

class DramaDrip : MainAPI() {
    override var mainUrl: String = runBlocking {
        DramaDripProvider.getDomains()?.dramadrip ?: "https://dramadrip.com"
    }
    override var name = "DramaDrip"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.AsianDrama, TvType.TvSeries)

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
        val titleElement = this.selectFirst("h2.entry-title") ?: return null
        val title = titleElement.text().substringAfter("Download").trim()
        val href = titleElement.selectFirst("a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, fixUrl(href, mainUrl), TvType.Movie) {
            this.posterUrl = fixUrl(posterUrl ?: "", mainUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}").document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        Log.d("DramaDrip", "Loading URL: $url")
        val document = app.get(url).document

        // Extract basic metadata
        val titleElement = document.selectFirst("div.wp-block-column > h2.wp-block-heading")
        val title = titleElement?.text()?.substringBefore("(")?.trim() ?: "Unknown Title"
        val year = titleElement?.text()?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val description = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val tags = document.select("div.mt-2 span.badge").map { it.text() }

        // Extract TMDb and IMDb IDs if available
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

        Log.d("DramaDrip", "Extracted IDs - IMDb: $imdbId, TMDb: $tmdbId, Type: $tmdbType")

        // Try to get enhanced metadata from TMDb
        val tmdbData = if (!tmdbId.isNullOrEmpty() && !tmdbType.isNullOrEmpty()) {
            fetchTMDbData(tmdbId!!, tmdbType!!)
        } else null

        val finalTitle = tmdbData?.title ?: tmdbData?.name ?: title
        val finalDescription = tmdbData?.overview ?: description ?: "No description available"
        val finalYear = tmdbData?.release_date?.substringBefore("-")?.toIntOrNull() 
            ?: tmdbData?.first_air_date?.substringBefore("-")?.toIntOrNull() 
            ?: year

        val finalPosterUrl = getTMDbImageUrl(tmdbData?.poster_path) ?: fixUrl(posterUrl, mainUrl)
        val backgroundUrl = getTMDbImageUrl(tmdbData?.backdrop_path, "w1280") ?: finalPosterUrl

        Log.d("DramaDrip", "Final metadata - Title: $finalTitle, Year: $finalYear")

        // Extract all streaming links
        val allLinks = extractAllLinks(document, url)
        Log.d("DramaDrip", "Extracted ${allLinks.size} links")

        // Determine content type
        val isMovie = allLinks.isNotEmpty() && !url.contains("/series/") && !url.contains("/drama/")
        
        if (isMovie) {
            Log.d("DramaDrip", "Creating movie response")
            return newMovieLoadResponse(finalTitle, url, TvType.Movie, allLinks.toJson()) {
                this.posterUrl = finalPosterUrl
                this.backgroundPosterUrl = backgroundUrl
                this.year = finalYear
                this.plot = finalDescription
                this.tags = tags
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        } else {
            Log.d("DramaDrip", "Creating TV series response")
            // For TV series, create episodes from links
            val episodes = allLinks.mapIndexed { index, link ->
                newEpisode(link) {
                    this.name = "Episode ${index + 1}"
                    this.episode = index + 1
                }
            }

            return newTvSeriesLoadResponse(finalTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = finalPosterUrl
                this.backgroundPosterUrl = backgroundUrl
                this.year = finalYear
                this.plot = finalDescription
                this.tags = tags
                addImdbId(imdbId)
                addTMDbId(tmdbId)
            }
        }
    }

    private suspend fun extractAllLinks(document: Element, pageUrl: String): List<String> {
        val links = mutableListOf<String>()

        // Extract direct download buttons
        document.select("div.wp-block-button > a").forEach { button ->
            val href = button.attr("href")
            if (href.isNotBlank()) {
                links.add(fixUrl(href, mainUrl))
            }
        }

        // Extract movie buttons
        document.select("div.wp-block-button.movie_btn a").forEach { button ->
            val href = button.attr("href")
            if (href.isNotBlank()) {
                links.add(fixUrl(href, mainUrl))
            }
        }

        Log.d("DramaDrip", "Found ${links.size} raw links")

        // Process safelinks and get direct streaming URLs
        val processedLinks = mutableListOf<String>()
        links.forEach { link ->
            try {
                Log.d("DramaDrip", "Processing link: $link")
                val processedLink = when {
                    link.contains("safelink=") -> {
                        Log.d("DramaDrip", "Bypassing safelink: $link")
                        cinematickitloadBypass(link)
                    }
                    link.contains("unblockedgames") || link.contains("examzculture") -> {
                        Log.d("DramaDrip", "Bypassing hrefli: $link")
                        bypassHrefli(link)
                    }
                    else -> link
                }

                if (!processedLink.isNullOrBlank()) {
                    Log.d("DramaDrip", "Processed link: $processedLink")
                    // If it's a page with multiple links, extract them
                    if (processedLink.contains("modpro") || processedLink.contains("/series/") || processedLink.contains("/movie/")) {
                        try {
                            Log.d("DramaDrip", "Extracting inner links from: $processedLink")
                            val linkDoc = app.get(processedLink).document
                            linkDoc.select("a[href*='jeniusplay'], a[href*='stream'], div.wp-block-button a").forEach { innerLink ->
                                val innerHref = innerLink.attr("href")
                                if (innerHref.isNotBlank() && !innerHref.contains("#")) {
                                    val finalHref = fixUrl(innerHref, mainUrl)
                                    processedLinks.add(finalHref)
                                    Log.d("DramaDrip", "Added inner link: $finalHref")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DramaDrip", "Error extracting inner links: ${e.message}")
                            processedLinks.add(processedLink)
                        }
                    } else {
                        processedLinks.add(processedLink)
                    }
                }
            } catch (e: Exception) {
                Log.e("DramaDrip", "Error processing link $link: ${e.message}")
            }
        }

        val distinctLinks = processedLinks.distinct().filter { it.isNotBlank() }
        Log.d("DramaDrip", "Final distinct links: ${distinctLinks.size}")
        return distinctLinks
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("DramaDrip", "loadLinks called with data: ${data.take(100)}...")
        val links = tryParseJson<List<String>>(data).orEmpty()
        if (links.isEmpty()) {
            Log.e("DramaDrip", "No links found in loadLinks")
            return false
        }

        Log.d("DramaDrip", "Processing ${links.size} links in loadLinks")
        var foundLinks = false

        links.forEach { link ->
            try {
                Log.d("DramaDrip", "Processing link: $link")
                when {
                    link.contains("jeniusplay.com") -> {
                        Log.d("DramaDrip", "Using Jeniusplay extractor")
                        loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
                        foundLinks = true
                    }
                    else -> {
                        Log.d("DramaDrip", "Using default extractor")
                        loadExtractor(link, "$mainUrl/", subtitleCallback, callback)
                        foundLinks = true
                    }
                }
            } catch (e: Exception) {
                Log.e("DramaDrip", "Error in loadLinks for $link: ${e.message}")
            }
        }

        Log.d("DramaDrip", "loadLinks completed. Found links: $foundLinks")
        return foundLinks
    }
}
