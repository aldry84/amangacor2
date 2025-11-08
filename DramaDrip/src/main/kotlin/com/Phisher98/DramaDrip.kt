// DramaDrip/src/main/kotlin/com/Phisher98/DramaDrip.kt

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
import java.util.concurrent.TimeUnit

class DramaDrip : MainAPI() {
    override var mainUrl: String = "https://dramadrip.com"
    
    init {
        runBlocking {
            mainUrl = DramaDripProvider.getDomains()?.dramadrip ?: "https://dramadrip.com"
        }
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = try {
            app.get("$mainUrl/${request.data}/page/$page").document
        } catch (e: Exception) {
            Log.e("DramaDrip", "Failed to load main page: ${e.message}")
            return newHomePageResponse(emptyList(), hasNext = false)
        }
        
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
        val title = this.selectFirst("h2.entry-title")?.text()
            ?.substringAfter("Download")
            ?.trim()
            ?: return null
            
        val href = this.select("h2.entry-title > a").attr("href")
        val imgElement = this.selectFirst("img")
        
        val posterUrl = extractBestImageUrl(imgElement)
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun extractBestImageUrl(imgElement: Element?): String? {
        if (imgElement == null) return null
        
        val srcset = imgElement.attr("srcset")
        val highestResUrl = srcset
            ?.split(",")
            ?.map { it.trim() }
            ?.mapNotNull { part ->
                val segments = part.split(" ").filter { it.isNotBlank() }
                when {
                    segments.size >= 2 -> {
                        val url = segments[0]
                        val width = segments[1].removeSuffix("w").toIntOrNull() ?: 0
                        url to width
                    }
                    else -> null
                }
            }
            ?.maxByOrNull { it.second }
            ?.first

        return highestResUrl ?: imgElement.attr("src")
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = try {
            app.get("$mainUrl/?s=${query.encodeUrl()}").document
        } catch (e: Exception) {
            Log.e("DramaDrip", "Search failed for query: $query - ${e.message}")
            return emptyList()
        }
        
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val document = try {
            app.get(url).document
        } catch (e: Exception) {
            throw ErrorException("Failed to load content from: $url")
        }

        // Ekstrak metadata IDs
        val metadata = extractMetadata(document)
        val (imdbId, tmdbId, tmdbType) = metadata
        
        val tvType = determineTvType(tmdbType)
        val image = document.select("meta[property=og:image]").attr("content")
        
        val title = extractTitle(document) ?: "Unknown Title"
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = extractYear(document)
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()

        // Fetch enriched metadata
        val enrichedData = fetchEnrichedMetadata(imdbId, tmdbId, tvType, descriptions)
        val (description, cast, background) = enrichedData

        val hrefs = extractDownloadLinks(document)
        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")
        val recommendations = extractRecommendations(document)

        return if (tvType == TvType.TvSeries) {
            createTvSeriesResponse(
                title, url, document, imdbId, tmdbId, year, description, 
                background, tags, trailer, cast, recommendations
            )
        } else {
            createMovieResponse(
                title, url, imdbId, tmdbId, hrefs, year, description,
                background, tags, trailer, cast, recommendations
            )
        }
    }

    private fun extractMetadata(document: Element): Triple<String?, String?, String?> {
        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null) {
                imdbId = Regex("tt\\d+").find(text)?.value
            }
            if (tmdbId == null && tmdbType == null) {
                val match = Regex("/(movie|tv)/(\\d+)").find(text)
                if (match != null) {
                    tmdbType = match.groupValues[1]
                    tmdbId = match.groupValues[2]
                }
            }
        }
        return Triple(imdbId, tmdbId, tmdbType)
    }

    private fun determineTvType(tmdbType: String?): TvType {
        return when {
            tmdbType.equals("movie", ignoreCase = true) -> TvType.Movie
            else -> TvType.TvSeries
        }
    }

    private fun extractTitle(document: Element): String? {
        return document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")
            ?.trim()
    }

    private fun extractYear(document: Element): Int? {
        return document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")
            ?.substringBefore(")")
            ?.toIntOrNull()
    }

    private suspend fun fetchEnrichedMetadata(
        imdbId: String?, 
        tmdbId: String?, 
        tvType: TvType,
        fallbackDescription: String?
    ): Triple<String?, List<String>, String> {
        var description = fallbackDescription
        var cast = emptyList<String>()
        var background = ""

        if (imdbId != null) {
            try {
                val idToUse = tmdbId ?: imdbId
                val endpoint = if (tmdbId != null) if (tvType == TvType.TvSeries) "series" else "movie" else "imdb"
                
                val jsonResponse = app.get("$cinemeta_url/$endpoint/$idToUse.json").text
                if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                    val responseData = Gson().fromJson(jsonResponse, ResponseData::class.java)
                    responseData?.meta?.let { meta ->
                        description = meta.description ?: description
                        cast = meta.cast ?: emptyList()
                        background = meta.background ?: ""
                    }
                }
            } catch (e: Exception) {
                Log.w("DramaDrip", "Failed to fetch enriched metadata: ${e.message}")
            }
        }
        
        return Triple(description, cast, background)
    }

    private suspend fun extractDownloadLinks(document: Element): List<String> {
        return document.select("div.wp-block-button > a")
            .mapNotNull { linkElement ->
                val link = linkElement.attr("href")
                // PERBAIKAN: Ganti cinematickitloadBypass dengan implementasi langsung
                val actualLink = safeBypass(link) ?: return@mapNotNull null
                try {
                    val page = app.get(actualLink).document
                    page.select("div.wp-block-button.movie_btn a").eachAttr("href")
                } catch (e: Exception) {
                    Log.w("DramaDrip", "Failed to extract links from: $actualLink")
                    emptyList()
                }
            }.flatten()
    }

    private fun extractRecommendations(document: Element): List<SearchResponse> {
        return document.select("div.entry-related-inner-content article").mapNotNull {
            val recName = it.select("h3").text().substringAfter("Download").trim()
            val recHref = it.select("h3 a").attr("href")
            val recPosterUrl = it.select("img").attr("src")
            
            if (recName.isNotBlank() && recHref.isNotBlank()) {
                newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                    this.posterUrl = recPosterUrl
                }
            } else {
                null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun createTvSeriesResponse(
        title: String, url: String, document: Element, 
        imdbId: String?, tmdbId: String?, year: Int?, description: String?,
        background: String, tags: List<String>, trailer: String?, 
        cast: List<String>, recommendations: List<SearchResponse>
    ): LoadResponse {
        
        val episodes = extractTvSeriesEpisodes(document, imdbId, tmdbId)
        
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.backgroundPosterUrl = background.ifBlank { null }
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

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun extractTvSeriesEpisodes(
        document: Element, 
        imdbId: String?, 
        tmdbId: String?
    ): List<Episode> {
        val tvSeriesEpisodes = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
        val seasonBlocks = document.select("div.su-accordion h2")

        for (seasonHeader in seasonBlocks) {
            val seasonText = seasonHeader.text()
            
            // Skip ZIP seasons
            if (seasonText.contains("ZIP", ignoreCase = true)) {
                Log.d("DramaDrip", "Skipping ZIP season: $seasonText")
                continue
            }

            val season = extractSeasonNumber(seasonText) ?: continue
            val qualityLinks = extractQualityLinks(seasonHeader)

            for (qualityPageLink in qualityLinks) {
                try {
                    val finalLink = if (qualityPageLink.contains("modpro")) {
                        qualityPageLink 
                    } else {
                        safeBypass(qualityPageLink) ?: continue
                    }
                    
                    val episodeDoc = app.get(finalLink).document
                    extractEpisodesFromPage(episodeDoc, season, tvSeriesEpisodes)
                } catch (e: Exception) {
                    Log.e("DramaDrip", "Failed to process quality link: $qualityPageLink - ${e.message}")
                }
            }
        }

        return tvSeriesEpisodes.map { (seasonEpisode, links) ->
            val (season, episode) = seasonEpisode
            val vidSrcData = createVidSrcData(TvType.TvSeries, imdbId, tmdbId, season, episode)
            val allLinks = listOf(vidSrcData) + links.distinct()
            
            newEpisode(allLinks.toJson()) {
                this.name = "Episode $episode"
                this.season = season
                this.episode = episode
            }
        }
    }

    private fun extractSeasonNumber(seasonText: String): Int? {
        val patterns = listOf(
            Regex("""season\s*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""s(\d+)""", RegexOption.IGNORE_CASE)
        )
        
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(seasonText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    private fun extractQualityLinks(seasonHeader: Element): List<String> {
        var linksBlock = seasonHeader.nextElementSibling()
        if (linksBlock?.select("div.wp-block-button")?.isEmpty() != false) {
            linksBlock = seasonHeader.parent()?.selectFirst("div.wp-block-button")
        }

        return linksBlock?.select("div.wp-block-button a")
            ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
            ?.distinct()
            ?: emptyList()
    }

    private fun extractEpisodesFromPage(
        episodeDoc: Element, 
        season: Int, 
        episodesMap: MutableMap<Pair<Int, Int>, MutableList<String>>
    ) {
        episodeDoc.select("a").forEach { element ->
            val href = element.attr("href")
            val text = element.text()
            
            if (href.isNotBlank()) {
                val episodeNum = extractEpisodeNumber(text)
                if (episodeNum != null) {
                    val key = season to episodeNum
                    episodesMap.getOrPut(key) { mutableListOf() }.add(href)
                }
            }
        }
    }

    private fun extractEpisodeNumber(text: String): Int? {
        return Regex("""(?:Episode|Ep|E)?\s*0*(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun createMovieResponse(
        title: String, url: String, imdbId: String?, tmdbId: String?,
        hrefs: List<String>, year: Int?, description: String?, background: String,
        tags: List<String>, trailer: String?, cast: List<String>, 
        recommendations: List<SearchResponse>
    ): LoadResponse {
        
        val vidSrcData = createVidSrcData(TvType.Movie, imdbId, tmdbId)
        val allHrefs = listOf(vidSrcData) + hrefs.distinct()
        
        return newMovieLoadResponse(title, url, TvType.Movie, allHrefs) {
            this.backgroundPosterUrl = background.ifBlank { null }
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

    // Helper function untuk membuat data VidSrc
    private fun createVidSrcData(
        type: TvType, 
        imdbId: String?, 
        tmdbId: String?, 
        season: Int = 0, 
        episode: Int = 0
    ): String {
        return "${type.name}|${imdbId.orEmpty()}|${tmdbId.orEmpty()}|$season|$episode"
    }

    // PERBAIKAN: Fungsi bypass yang sederhana sebagai pengganti
    private suspend fun safeBypass(url: String): String? {
        return try {
            if (url.contains("safelink") || url.contains("modpro")) {
                // Coba bypass sederhana - langsung return URL untuk sekarang
                // Dalam implementasi nyata, Anda perlu mengekstrak URL dari halaman safelink
                app.get(url).url
            } else {
                url
            }
        } catch (e: Exception) {
            Log.w("DramaDrip", "Bypass failed for: $url - ${e.message}")
            url // Fallback ke URL asli
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
            Log.e("DramaDrip", "No links found in data: $data")
            return false
        }
        
        val vidSrcExtractor = VidSrcEmbedExtractor()
        var successCount = 0

        for (link in links) {
            try {
                if (link.startsWith(TvType.TvSeries.name) || link.startsWith(TvType.Movie.name)) {
                    // Handle VidSrc links
                    vidSrcExtractor.getUrl(link, null, subtitleCallback, callback)
                    successCount++
                } else if ("safelink=" in link || "unblockedgames" in link || "examzculture" in link) {
                    // Handle safelink bypass
                    val finalLink = safeBypass(link)
                    if (finalLink != null) {
                        loadExtractor(finalLink, subtitleCallback, callback)
                        successCount++
                    } else {
                        Log.w("LoadLinks", "Bypass returned null for link: $link")
                    }
                } else {
                    // Direct links
                    loadExtractor(link, subtitleCallback, callback)
                    successCount++
                }
            } catch (e: Exception) {
                Log.e("DramaDrip", "Failed to load link: $link - ${e.message}")
            }
        }

        return successCount > 0
    }

    // Extension function untuk URL encoding
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

// Custom exception for better error handling
class ErrorException(message: String) : Exception(message)
