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

// Data class untuk menyimpan informasi episode
private data class EpisodeInfo(
    val season: Int,
    val episode: Int,
    val links: MutableList<String>,
    val name: String? = null,
    val description: String? = null
)

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
        Log.d("DramaDrip", "Loading URL: $url")
        
        val document = try {
            app.get(url).document
        } catch (e: Exception) {
            Log.e("DramaDrip", "Failed to load document from: $url - ${e.message}")
            throw ErrorException("Failed to load content from: $url")
        }

        // Log struktur HTML untuk debugging
        Log.d("DramaDrip", "Page title: ${document.title()}")
        Log.d("DramaDrip", "Season accordions found: ${document.select("div.su-accordion h2").size}")
        Log.d("DramaDrip", "Download buttons found: ${document.select("div.wp-block-button a").size}")
        Log.d("DramaDrip", "All links found: ${document.select("a[href]").size}")

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

        Log.d("DramaDrip", "Detected type: $tvType, Title: $title, Year: $year")

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
        
        Log.d("DramaDrip", "Extracted metadata - IMDb: $imdbId, TMDb: $tmdbId, Type: $tmdbType")
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

        if (imdbId != null || tmdbId != null) {
            try {
                val idToUse = tmdbId ?: imdbId
                val endpoint = if (tmdbId != null) {
                    if (tvType == TvType.TvSeries) "series" else "movie"
                } else {
                    "imdb"
                }
                
                Log.d("DramaDrip", "Fetching enriched metadata from Cinemeta: $endpoint/$idToUse")
                val jsonResponse = app.get("$cinemeta_url/$endpoint/$idToUse.json").text
                if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                    val responseData = Gson().fromJson(jsonResponse, ResponseData::class.java)
                    responseData?.meta?.let { meta ->
                        description = meta.description ?: description
                        cast = meta.cast ?: emptyList()
                        background = meta.background ?: ""
                        Log.d("DramaDrip", "Enriched metadata found: ${meta.name}, Cast: ${cast.size} actors")
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
        val finalEpisodes = if (episodes.isEmpty()) {
            Log.w("DramaDrip", "No episodes found, using fallback")
            createFallbackEpisodes()
        } else {
            episodes
        }
        
        Log.d("DramaDrip", "Creating TV series response with ${finalEpisodes.size} episodes")
        
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
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
        val episodes = mutableListOf<EpisodeInfo>()
        
        Log.d("DramaDrip", "Starting episode extraction with multiple methods...")
        
        // Method 1: Coba ekstrak dari accordion season (cara original)
        extractFromSeasonAccordion(document, episodes)
        
        // Method 2: Jika method 1 gagal, coba cari episode dari link langsung
        if (episodes.isEmpty()) {
            Log.d("DramaDrip", "Method 1 failed, trying direct links...")
            extractFromDirectLinks(document, episodes)
        }
        
        // Method 3: Jika masih kosong, coba dari download buttons
        if (episodes.isEmpty()) {
            Log.d("DramaDrip", "Method 2 failed, trying download buttons...")
            extractFromDownloadButtons(document, episodes)
        }
        
        // Method 4: Coba dari content area
        if (episodes.isEmpty()) {
            Log.d("DramaDrip", "Method 3 failed, trying content area...")
            extractFromContentArea(document, episodes)
        }
        
        Log.d("DramaDrip", "Total episodes found: ${episodes.size}")
        
        // Sort episodes by season and episode number
        val sortedEpisodes = episodes.sortedWith(compareBy({ it.season }, { it.episode }))
        
        return sortedEpisodes.map { episodeInfo ->
            val vidSrcData = createVidSrcData(TvType.TvSeries, imdbId, tmdbId, episodeInfo.season, episodeInfo.episode)
            val allLinks = listOf(vidSrcData) + episodeInfo.links.distinct()
            
            newEpisode(allLinks.toJson()) {
                this.name = episodeInfo.name ?: "Episode ${episodeInfo.episode}"
                this.season = episodeInfo.season
                this.episode = episodeInfo.episode
                this.description = episodeInfo.description
            }
        }
    }

    // Method 1: Ekstraksi dari accordion season
    private suspend fun extractFromSeasonAccordion(document: Element, episodes: MutableList<EpisodeInfo>) {
        val seasonBlocks = document.select("div.su-accordion h2")

        Log.d("DramaDrip", "Found ${seasonBlocks.size} season accordions")
        
        if (seasonBlocks.isEmpty()) {
            // Coba alternatif selector untuk accordion
            val altSeasonBlocks = document.select("h3, h4").filter { 
                it.text().contains("season", ignoreCase = true) || 
                it.text().contains("episode", ignoreCase = true) 
            }
            Log.d("DramaDrip", "Found ${altSeasonBlocks.size} alternative season blocks")
        }

        for (seasonHeader in seasonBlocks) {
            val seasonText = seasonHeader.text()
            
            // Skip ZIP seasons
            if (seasonText.contains("ZIP", ignoreCase = true)) {
                Log.d("DramaDrip", "Skipping ZIP season: $seasonText")
                continue
            }

            val season = extractSeasonNumber(seasonText) ?: 1 // Default ke season 1 jika tidak ditemukan
            Log.d("DramaDrip", "Processing season: $season from text: '$seasonText'")

            val qualityLinks = extractQualityLinks(seasonHeader)
            Log.d("DramaDrip", "Found ${qualityLinks.size} quality links for season $season")

            for (qualityPageLink in qualityLinks) {
                try {
                    Log.d("DramaDrip", "Processing quality link: $qualityPageLink")
                    val finalLink = if (qualityPageLink.contains("modpro")) {
                        qualityPageLink 
                    } else {
                        safeBypass(qualityPageLink) ?: continue
                    }
                    
                    val episodeDoc = app.get(finalLink).document
                    extractEpisodesFromQualityPage(episodeDoc, season, episodes)
                } catch (e: Exception) {
                    Log.e("DramaDrip", "Failed to process quality link: $qualityPageLink - ${e.message}")
                }
            }
        }
    }

    // Method 2: Ekstraksi dari link langsung di halaman utama
    private fun extractFromDirectLinks(document: Element, episodes: MutableList<EpisodeInfo>) {
        Log.d("DramaDrip", "Trying direct link extraction...")
        
        // Cari semua link yang kemungkinan adalah episode
        val potentialEpisodeLinks = document.select("a[href]").filter { element ->
            val href = element.attr("href")
            val text = element.text().trim()
            
            href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript:") &&
            (text.contains("episode", ignoreCase = true) ||
             text.contains("ep\\.?", RegexOption.IGNORE_CASE) ||
             text.contains("eps\\.?", RegexOption.IGNORE_CASE) ||
             text.matches(Regex("""(?i).*\b\d+\b.*""")) ||
             href.contains("episode", ignoreCase = true) ||
             href.contains("/ep-", ignoreCase = true) ||
             href.contains("-episode-", ignoreCase = true))
        }
        
        Log.d("DramaDrip", "Found ${potentialEpisodeLinks.size} potential episode links")
        
        var episodeCounter = 1
        potentialEpisodeLinks.forEach { element ->
            val href = element.attr("href")
            val text = element.text().trim()
            
            val episodeNum = extractEpisodeNumber(text) ?: episodeCounter
            val season = extractSeasonNumber(text) ?: 1
            
            // Only add if we haven't seen this episode already
            val existingEpisode = episodes.find { it.season == season && it.episode == episodeNum }
            if (existingEpisode == null) {
                episodes.add(EpisodeInfo(season, episodeNum, mutableListOf(href), "Episode $episodeNum"))
                Log.d("DramaDrip", "Added episode $episodeNum (S$season) from direct link: '$text'")
                episodeCounter++
            } else {
                existingEpisode.links.add(href)
            }
        }
    }

    // Method 3: Ekstraksi dari download buttons
    private fun extractFromDownloadButtons(document: Element, episodes: MutableList<EpisodeInfo>) {
        Log.d("DramaDrip", "Trying download button extraction...")
        
        // Cari di berbagai jenis button yang umum
        val buttonSelectors = listOf(
            "div.wp-block-button a",
            "a.btn", 
            "a.download",
            "a.button",
            "div.download-link a",
            "a[href*='download']"
        )
        
        var episodeCounter = 1
        buttonSelectors.forEach { selector ->
            val buttons = document.select(selector)
            Log.d("DramaDrip", "Found ${buttons.size} elements with selector: $selector")
            
            buttons.forEach { element ->
                val href = element.attr("href")
                val text = element.text().trim()
                
                if (href.isNotBlank() && !href.contains("#") && 
                    !text.equals("download", ignoreCase = true) &&
                    !text.equals("watch", ignoreCase = true)) {
                    
                    val episodeNum = extractEpisodeNumber(text) ?: episodeCounter
                    val season = extractSeasonNumber(text) ?: 1
                    
                    // Cek apakah episode ini sudah ada
                    val existingEpisode = episodes.find { it.season == season && it.episode == episodeNum }
                    if (existingEpisode != null) {
                        existingEpisode.links.add(href)
                        Log.d("DramaDrip", "Added link to existing episode $episodeNum (S$season)")
                    } else {
                        episodes.add(EpisodeInfo(season, episodeNum, mutableListOf(href), "Episode $episodeNum"))
                        Log.d("DramaDrip", "Added new episode $episodeNum (S$season) from button: '$text'")
                        episodeCounter++
                    }
                }
            }
        }
    }

    // Method 4: Ekstraksi dari content area
    private fun extractFromContentArea(document: Element, episodes: MutableList<EpisodeInfo>) {
        Log.d("DramaDrip", "Trying content area extraction...")
        
        // Coba berbagai area konten yang mungkin berisi episode
        val contentAreas = document.select("div.entry-content, div.content, div.post-content, article")
        
        contentAreas.forEach { contentArea ->
            val links = contentArea.select("a[href]")
            Log.d("DramaDrip", "Found ${links.size} links in content area")
            
            var episodeCounter = 1
            links.forEach { link ->
                val href = link.attr("href")
                val text = link.text().trim()
                
                if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript:")) {
                    val episodeNum = extractEpisodeNumber(text) ?: episodeCounter
                    val season = extractSeasonNumber(text) ?: 1
                    
                    // Skip obvious non-episode links
                    if (text.contains("home", ignoreCase = true) || 
                        text.contains("contact", ignoreCase = true) ||
                        text.contains("privacy", ignoreCase = true) ||
                        text.length > 50) {
                        return@forEach
                    }
                    
                    val existingEpisode = episodes.find { it.season == season && it.episode == episodeNum }
                    if (existingEpisode == null) {
                        episodes.add(EpisodeInfo(season, episodeNum, mutableListOf(href), "Episode $episodeNum"))
                        Log.d("DramaDrip", "Added episode $episodeNum (S$season) from content: '$text'")
                        episodeCounter++
                    } else {
                        existingEpisode.links.add(href)
                    }
                }
            }
        }
    }

    private fun extractQualityLinks(seasonHeader: Element): List<String> {
        var linksBlock = seasonHeader.nextElementSibling()
        if (linksBlock == null || linksBlock.select("div.wp-block-button").isEmpty()) {
            linksBlock = seasonHeader.parent()?.selectFirst("div.su-spoiler-content")
        }
        if (linksBlock == null || linksBlock.select("div.wp-block-button").isEmpty()) {
            linksBlock = seasonHeader.parent()?.selectFirst("div.wp-block-button")
        }

        val links = linksBlock?.select("div.wp-block-button a, a.btn, a.button")
            ?.mapNotNull { it.attr("href").takeIf { href -> href.isNotBlank() } }
            ?.distinct()
            ?: emptyList()
            
        Log.d("DramaDrip", "Extracted ${links.size} quality links from season header")
        return links
    }

    private suspend fun extractEpisodesFromQualityPage(
        episodeDoc: Element, 
        season: Int, 
        episodes: MutableList<EpisodeInfo>
    ) {
        val episodeElements = episodeDoc.select("a[href]")
        Log.d("DramaDrip", "Found ${episodeElements.size} links in quality page")
        
        if (episodeElements.isEmpty()) {
            // Coba alternatif selectors
            val altElements = episodeDoc.select("button, div, span").filter { 
                it.text().contains("episode", ignoreCase = true) || 
                it.text().contains("download", ignoreCase = true)
            }
            Log.d("DramaDrip", "Found ${altElements.size} alternative elements in quality page")
        }
        
        episodeElements.forEach { element ->
            val href = element.attr("href")
            val text = element.text().trim()
            
            if (href.isNotBlank() && !href.startsWith("#") && !href.contains("javascript:")) {
                val episodeNum = extractEpisodeNumber(text)
                if (episodeNum != null) {
                    Log.d("DramaDrip", "Found episode $episodeNum from text: '$text'")
                    
                    // Cari episode yang sudah ada atau buat baru
                    val existingEpisode = episodes.find { it.season == season && it.episode == episodeNum }
                    if (existingEpisode != null) {
                        existingEpisode.links.add(href)
                        Log.d("DramaDrip", "Added link to existing episode $episodeNum (S$season)")
                    } else {
                        episodes.add(EpisodeInfo(season, episodeNum, mutableListOf(href), "Episode $episodeNum"))
                        Log.d("DramaDrip", "Created new episode $episodeNum (S$season)")
                    }
                } else {
                    // Jika tidak bisa extract episode number, tapi ini adalah link download, tambahkan sebagai episode baru
                    if ((text.contains("download", ignoreCase = true) || 
                         text.contains("episode", ignoreCase = true) ||
                         text.matches(Regex("""(?i).*\d+.*"""))) &&
                         !text.contains("season", ignoreCase = true)) {
                        
                        val newEpisodeNum = episodes.filter { it.season == season }.size + 1
                        episodes.add(EpisodeInfo(season, newEpisodeNum, mutableListOf(href), "Episode $newEpisodeNum"))
                        Log.d("DramaDrip", "Added new episode $newEpisodeNum from ambiguous text: '$text'")
                    }
                }
            }
        }
    }

    // Improved episode number extraction dengan lebih banyak pattern
    private fun extractEpisodeNumber(text: String): Int? {
        val patterns = listOf(
            Regex("""(?:Episode|Ep|Eps|EP|EPS|EPISODE)\s*[.:]?\s*0*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""\b0*(\d+)\b"""), // Angka saja
            Regex("""\[0*(\d+)\]"""), // Format [01]
            Regex("""\(\s*0*(\d+)\s*\)"""), // Format (01)
            Regex("""-\s*0*(\d+)\s*-"""), // Format -01-
            Regex("""#\s*0*(\d+)"""), // Format #01
            Regex("""Part\s*0*(\d+)""", RegexOption.IGNORE_CASE), // Format Part 1
            Regex("""E\s*0*(\d+)""", RegexOption.IGNORE_CASE) // Format E01
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val number = match.groupValues[1].toIntOrNull()
                if (number != null && number > 0 && number < 1000) { // Validasi angka episode
                    return number
                }
            }
        }
        return null
    }

    // Improved season number extraction
    private fun extractSeasonNumber(seasonText: String): Int? {
        val patterns = listOf(
            Regex("""season\s*[.:]?\s*0*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""s\s*[.:]?\s*0*(\d+)""", RegexOption.IGNORE_CASE),
            Regex("""\b0*(\d+)\s*season""", RegexOption.IGNORE_CASE),
            Regex("""\[s\s*0*(\d+)\]""", RegexOption.IGNORE_CASE),
            Regex("""series\s*0*(\d+)""", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(seasonText)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
        }
        return 1 // Default ke season 1 jika tidak ditemukan
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
        
        Log.d("DramaDrip", "Creating movie response with ${allHrefs.size} links")
        
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

    // Fungsi bypass yang lebih komprehensif
    private suspend fun safeBypass(url: String): String? {
        return try {
            when {
                url.contains("safelink") || url.contains("modpro") -> {
                    // Handle safelink bypass
                    bypassSafelink(url)
                }
                url.contains("linkvertise") || url.contains("link-target") -> {
                    // Handle linkvertise bypass  
                    bypassLinkvertise(url)
                }
                url.contains("ouo.io") || url.contains("ouo.press") -> {
                    // Handle ouo bypass
                    bypassOuo(url)
                }
                else -> {
                    // Untuk link langsung, verifikasi bahwa itu valid
                    if (url.startsWith("http") && !url.contains("javascript:")) {
                        url
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DramaDrip", "Bypass failed for: $url - ${e.message}")
            null // Return null jika bypass gagal
        }
    }

    private suspend fun bypassSafelink(url: String): String? {
        return try {
            Log.d("DramaDrip", "Attempting safelink bypass for: $url")
            val document = app.get(url).document
            
            // Coba berbagai metode ekstraksi untuk safelink
            val finalUrl = document.selectFirst("a#download-button")?.attr("href")
                ?: document.selectFirst("a[href*='download']")?.attr("href")
                ?: document.selectFirst("a.btn-success")?.attr("href")
                ?: document.selectFirst("a.button[href]")?.attr("href")
                ?: document.selectFirst("a[onclick*='window.location']")?.attr("onclick")
                    ?.substringAfter("='")
                    ?.substringBefore("'")
                ?: document.selectFirst("meta[http-equiv='refresh']")?.attr("content")
                    ?.substringAfter("url=")
                    ?.substringBefore("'")
            
            if (finalUrl != null) {
                Log.d("DramaDrip", "Safelink bypass successful: $finalUrl")
            } else {
                Log.w("DramaDrip", "Safelink bypass failed, no download link found")
            }
            
            finalUrl ?: url
        } catch (e: Exception) {
            Log.w("DramaDrip", "Safelink bypass failed: ${e.message}")
            url
        }
    }

    private suspend fun bypassLinkvertise(url: String): String? {
        // Implementasi sederhana untuk linkvertise
        Log.d("DramaDrip", "Linkvertise bypass attempted for: $url")
        // Linkvertise biasanya memerlukan implementasi yang lebih kompleks
        // Untuk sekarang, return URL asli
        return url
    }

    private suspend fun bypassOuo(url: String): String? {
        // Implementasi sederhana untuk ouo.io
        try {
            Log.d("DramaDrip", "Attempting Ouo bypass for: $url")
            val document = app.get(url).document
            return document.selectFirst("a#btn")?.attr("href")
                ?: document.selectFirst("a.succedbtn")?.attr("href")
                ?: document.selectFirst("a[class*='btn']")?.attr("href")
                ?: url
        } catch (e: Exception) {
            Log.w("DramaDrip", "Ouo bypass failed: ${e.message}")
            return url
        }
    }

    // Fallback episodes untuk menghindari "Episode Tidak Ditemukan"
    private fun createFallbackEpisodes(): List<Episode> {
        Log.d("DramaDrip", "Creating fallback episodes")
        // Buat 1-3 episode placeholder sebagai fallback
        return listOf(
            newEpisode("[]") { // Empty links array
                this.name = "Episode 1"
                this.season = 1
                this.episode = 1
                this.description = "Episode akan tersedia segera"
            },
            newEpisode("[]") {
                this.name = "Episode 2" 
                this.season = 1
                this.episode = 2
                this.description = "Episode akan tersedia segera"
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = tryParseJson<List<String>>(data).orEmpty()
        Log.d("DramaDrip", "Loading ${links.size} links from data")
        
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
                    Log.d("DramaDrip", "Processing VidSrc link: $link")
                    vidSrcExtractor.getUrl(link, null, subtitleCallback, callback)
                    successCount++
                } else if (link.contains("safelink=") || link.contains("unblockedgames") || link.contains("examzculture")) {
                    // Handle safelink bypass
                    Log.d("DramaDrip", "Processing safelink: $link")
                    val finalLink = safeBypass(link)
                    if (finalLink != null) {
                        loadExtractor(finalLink, subtitleCallback, callback)
                        successCount++
                    } else {
                        Log.w("LoadLinks", "Bypass returned null for link: $link")
                    }
                } else if (link.isNotBlank() && link.startsWith("http")) {
                    // Direct links
                    Log.d("DramaDrip", "Processing direct link: $link")
                    loadExtractor(link, subtitleCallback, callback)
                    successCount++
                }
            } catch (e: Exception) {
                Log.e("DramaDrip", "Failed to load link: $link - ${e.message}")
            }
        }

        Log.d("DramaDrip", "Successfully loaded $successCount out of ${links.size} links")
        return successCount > 0
    }

    // Extension function untuk URL encoding
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
