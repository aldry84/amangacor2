package com.Phisher98

import android.os.Build
import androidx.annotation.RequiresApi
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
import java.net.URLEncoder

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
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = fixUrl(this.selectFirst("h2.entry-title > a")?.attr("href") ?: return null, mainUrl)
        val imgElement = this.selectFirst("img")
        
        val posterUrl = when {
            imgElement?.hasAttr("srcset") == true -> {
                imgElement.attr("srcset").split(",")
                    .mapNotNull { it.trim().split(" ").takeIf { it.size == 2 }?.let { pair -> pair[0] to pair[1].removeSuffix("w").toIntOrNull() } }
                    .maxByOrNull { it.second ?: 0 }?.first
            }
            else -> imgElement?.attr("src")
        }

        return newMovieSearchResponse(cleanTitle(title), href, TvType.Movie) {
            this.posterUrl = fixUrl(posterUrl ?: "", mainUrl)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=${URLEncoder.encode(query, "UTF-8")}").document
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        Log.d("DramaDrip", "Loading URL: $url")
        val document = app.get(url).document

        // Extract metadata
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim() ?: "Unknown Title"
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val description = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val image = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val tags = document.select("div.mt-2 span.badge").map { it.text() }

        // Extract all available links
        val hrefs = mutableListOf<String>()
        
        // Method 1: Direct download buttons
        document.select("div.wp-block-button > a").forEach { button ->
            val link = button.attr("href")
            if (link.isNotBlank()) {
                Log.d("DramaDrip", "Found button link: $link")
                hrefs.add(link)
            }
        }

        // Method 2: Movie buttons in content
        document.select("div.wp-block-button.movie_btn a").forEach { button ->
            val link = button.attr("href")
            if (link.isNotBlank()) {
                Log.d("DramaDrip", "Found movie button link: $link")
                hrefs.add(link)
            }
        }

        // Method 3: Try to bypass safelinks and get direct links
        val processedLinks = mutableListOf<String>()
        hrefs.forEach { link ->
            try {
                Log.d("DramaDrip", "Processing link: $link")
                val processedLink = when {
                    link.contains("safelink=") -> {
                        val bypassed = cinematickitloadBypass(link)
                        Log.d("DramaDrip", "Bypassed safelink: $bypassed")
                        bypassed
                    }
                    link.contains("unblockedgames") || link.contains("examzculture") -> {
                        val bypassed = bypassHrefli(link)
                        Log.d("DramaDrip", "Bypassed hrefli: $bypassed")
                        bypassed
                    }
                    else -> link
                }
                
                if (!processedLink.isNullOrBlank()) {
                    // If it's another page with multiple links, extract them
                    if (processedLink.contains("modpro") || processedLink.contains("/series/") || processedLink.contains("/movie/")) {
                        try {
                            val linkDoc = app.get(processedLink).document
                            linkDoc.select("div.wp-block-button a, a[href*='jeniusplay'], a[href*='stream']").forEach { innerLink ->
                                val href = innerLink.attr("href")
                                if (href.isNotBlank() && !href.contains("#") && !href.startsWith("javascript")) {
                                    Log.d("DramaDrip", "Found inner link: $href")
                                    processedLinks.add(fixUrl(href, mainUrl))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DramaDrip", "Error processing inner links: ${e.message}")
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

        Log.d("DramaDrip", "Final processed links: ${processedLinks.size}")
        processedLinks.forEachIndexed { index, link -> 
            Log.d("DramaDrip", "Link $index: $link")
        }

        // Determine content type and create response
        val isMovie = processedLinks.isNotEmpty() && !url.contains("/series/") && !url.contains("/drama/")
        val cleanTitle = cleanTitle(title)

        if (isMovie) {
            return newMovieLoadResponse(cleanTitle, url, TvType.Movie, processedLinks) {
                this.posterUrl = fixUrl(image, mainUrl)
                this.year = year
                this.plot = description
                this.tags = tags
            }
        } else {
            // For TV series, create simple episodes
            val episodes = processedLinks.mapIndexed { index, link ->
                newEpisode(link) {
                    this.name = "Episode ${index + 1}"
                    this.episode = index + 1
                }
            }

            return newTvSeriesLoadResponse(cleanTitle, url, TvType.TvSeries, episodes) {
                this.posterUrl = fixUrl(image, mainUrl)
                this.year = year
                this.plot = description
                this.tags = tags
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
        Log.d("DramaDrip", "loadLinks called with data: ${data.take(100)}...")
        
        // Handle both single link and list of links
        val links = if (data.startsWith("[")) {
            tryParseJson<List<String>>(data).orEmpty()
        } else {
            listOf(data)
        }

        if (links.isEmpty()) {
            Log.e("DramaDrip", "No links found in loadLinks")
            return false
        }

        Log.d("DramaDrip", "Processing ${links.size} links in loadLinks")
        var foundLinks = false

        links.forEach { link ->
            try {
                Log.d("DramaDrip", "Processing link in loadLinks: $link")
                
                when {
                    link.contains("jeniusplay.com") -> {
                        Log.d("DramaDrip", "Direct Jeniusplay link found")
                        invokeJeniusplay(link, subtitleCallback, callback)
                        foundLinks = true
                    }
                    link.contains("safelink=") -> {
                        val bypassed = cinematickitBypass(link)
                        if (!bypassed.isNullOrBlank()) {
                            Log.d("DramaDrip", "Bypassed safelink to: $bypassed")
                            loadExtractor(bypassed, "$mainUrl/", subtitleCallback, callback)
                            foundLinks = true
                        }
                    }
                    link.contains("unblockedgames") || link.contains("examzculture") -> {
                        val bypassed = bypassHrefli(link)
                        if (!bypassed.isNullOrBlank()) {
                            Log.d("DramaDrip", "Bypassed hrefli to: $bypassed")
                            loadExtractor(bypassed, "$mainUrl/", subtitleCallback, callback)
                            foundLinks = true
                        }
                    }
                    else -> {
                        Log.d("DramaDrip", "Using default extractor for: $link")
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

    private suspend fun invokeJeniusplay(
        url: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            Log.d("DramaDrip", "Invoking Jeniusplay for: $url")
            Jeniusplay2().getUrl(url, "$mainUrl/", subtitleCallback, callback)
        } catch (e: Exception) {
            Log.e("DramaDrip", "Jeniusplay extraction failed: ${e.message}")
            // Fallback to standard extractor
            loadExtractor(url, "$mainUrl/", subtitleCallback, callback)
        }
    }
}

// Improved cleanTitle function
fun cleanTitle(title: String): String {
    return title.replace("Download", "")
        .replace("(?i)full\\s*movie".toRegex(), "")
        .replace("(?i)season\\s*\\d+".toRegex(), "")
        .replace("\\s+".toRegex(), " ")
        .trim()
}
