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

// Import fungsi extractor kita
import com.Phisher98.DramaDripExtractor.invokeGomovies
import com.Phisher98.DramaDripExtractor.invokeIdlix
import com.Phisher98.DramaDripExtractor.invokeVidfast
import com.Phisher98.DramaDripExtractor.invokeVidlink
import com.Phisher98.DramaDripExtractor.invokeVidrock
import com.Phisher98.DramaDripExtractor.invokeVidsrc
import com.Phisher98.DramaDripExtractor.invokeVidsrccc
import com.Phisher98.DramaDripExtractor.invokeWyzie

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
        val document = app.get("$mainUrl/${request.data}/page/$page").documentLarge
        val home = document.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, home, false), true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title")?.text()?.substringAfter("Download") ?: return null
        val href = this.select("h2.entry-title > a").attr("href")
        val imgElement = this.selectFirst("img")
        val srcset = imgElement?.attr("srcset")
        val highestResUrl = srcset?.split(",")?.map { it.trim() }?.mapNotNull { val parts = it.split(" "); if (parts.size == 2) parts[0] to parts[1].removeSuffix("w").toIntOrNull() else null }?.maxByOrNull { it.second ?: 0 }?.first
        val posterUrl = highestResUrl ?: imgElement?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").documentLarge
        return document.select("article").mapNotNull { it.toSearchResult() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).documentLarge
        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null

        document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
            val text = li.text()
            if (imdbId == null && "imdb.com/title/tt" in text) imdbId = Regex("tt\\d+").find(text)?.value
            if (tmdbId == null && "themoviedb.org" in text) { Regex("/(movie|tv)/(\\d+)").find(text)?.let { tmdbType = it.groupValues[1]; tmdbId = it.groupValues[2] } }
        }
        val tvType = if (tmdbType?.contains("Movie", true) == true) TvType.Movie else TvType.TvSeries

        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()?.substringBefore("(")?.trim().toString()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val typeset = if (tvType == TvType.TvSeries) "series" else "movie"
        
        val responseData = if (tmdbId?.isNotEmpty() == true) {
            val jsonResponse = app.get("$cinemeta_url/$typeset/$imdbId.json").text
            if (jsonResponse.startsWith("{")) Gson().fromJson(jsonResponse, ResponseData::class.java) else null
        } else null
        
        var cast: List<String> = emptyList()
        var background: String = image
        var description: String? = null
        if (responseData != null) {
            description = responseData.meta?.description ?: descriptions
            cast = responseData.meta?.cast ?: emptyList()
            background = responseData.meta?.background ?: image
        }

        // Kita masih mengambil link asli DramaDrip sebagai backup
        val hrefs: List<String> = document.select("div.wp-block-button > a").mapNotNull { linkElement ->
            val link = linkElement.attr("href")
            val actual = cinematickitloadBypass(link) ?: return@mapNotNull null
            app.get(actual).documentLarge.select("div.wp-block-button.movie_btn a").eachAttr("href")
        }.flatten()

        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")
        val recommendations = document.select("div.entry-related-inner-content article").mapNotNull {
            newTvSeriesSearchResponse(it.select("h3").text().substringAfter("Download"), it.select("h3 a").attr("href"), TvType.TvSeries) { this.posterUrl = it.select("img").attr("src") }
        }

        if (tvType == TvType.TvSeries) {
            val tvSeriesEpisodes = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
            document.select("div.su-accordion h2").forEach { seasonHeader ->
                if (!seasonHeader.text().contains("ZIP", true)) {
                    val season = Regex("""S?e?a?s?o?n?\s*([0-9]+)""", RegexOption.IGNORE_CASE).find(seasonHeader.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (season != null) {
                        val linksBlock = seasonHeader.nextElementSibling() ?: seasonHeader.parent()?.selectFirst("div.wp-block-button")
                        linksBlock?.select("div.wp-block-button a")?.mapNotNull { it.attr("href").takeIf { h -> h.isNotBlank() } }?.distinct()?.forEach { qLink ->
                            try {
                                val rawLink = if (qLink.contains("modpro")) qLink else cinematickitloadBypass(qLink) ?: ""
                                app.get(rawLink).documentLarge.select("a").filter { it.text().matches(Regex("""(?i)(Episode|Ep|E)?\s*0*\d+""")) }.forEach { btn ->
                                    val epNo = Regex("""(?:Episode|Ep|E)?\s*0*([0-9]+)""", RegexOption.IGNORE_CASE).find(btn.text())?.groupValues?.getOrNull(1)?.toIntOrNull()
                                    if (epNo != null) tvSeriesEpisodes.getOrPut(season to epNo) { mutableListOf() }.add(btn.attr("href"))
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            val finalEpisodes = tvSeriesEpisodes.map { (seasonEpisode, links) ->
                val (season, epNo) = seasonEpisode
                val info = responseData?.meta?.videos?.find { it.season == season && it.episode == epNo }
                val linkData = LinkData(links.distinct(), tmdbId, imdbId, season, epNo, "tv", title, year)
                newEpisode(linkData.toJson()) {
                    this.name = info?.name ?: "Episode $epNo"
                    this.posterUrl = info?.thumbnail
                    this.season = season
                    this.episode = epNo
                    this.description = info?.overview
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
                this.backgroundPosterUrl = background; this.year = year; this.plot = description; this.tags = tags; this.recommendations = recommendations
                addTrailer(trailer); addActors(cast); addImdbId(imdbId); addTMDbId(tmdbId)
            }
        } else {
            val linkData = LinkData(hrefs, tmdbId, imdbId, null, null, "movie", title, year)
            return newMovieLoadResponse(title, url, TvType.Movie, linkData.toJson()) {
                this.backgroundPosterUrl = background; this.year = year; this.plot = description; this.tags = tags; this.recommendations = recommendations
                addTrailer(trailer); addActors(cast); addImdbId(imdbId); addTMDbId(tmdbId)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val parsedData = tryParseJson<LinkData>(data) ?: return false
        
        // Eksekusi Paralel Semua Extractor (Hybrid: DramaDrip Asli + Adicinemax Sources)
        runAllAsync(
            // 1. Sumber Asli DramaDrip (Backup)
            {
               parsedData.links.forEach { link ->
                   try {
                       val finalLink = if (link.contains("safelink=")) cinematickitBypass(link) else if (link.contains("unblockedgames") || link.contains("examzculture")) bypassHrefli(link) else link
                       if (finalLink != null) loadExtractor(finalLink, subtitleCallback, callback)
                   } catch (_: Exception) {}
               }
            },
            // 2. Extractor Wyzie (Subtitle)
            { invokeWyzie(parsedData.tmdbId, parsedData.season, parsedData.episode, subtitleCallback) },
            // 3. Extractor Idlix
            { invokeIdlix(parsedData.title, parsedData.year, parsedData.season, parsedData.episode, subtitleCallback, callback) },
            // 4. Extractor Vidrock
            { invokeVidrock(parsedData.tmdbId, parsedData.season, parsedData.episode, subtitleCallback, callback) },
            // 5. Extractor Gomovies
            { invokeGomovies(parsedData.title, parsedData.year, parsedData.season, parsedData.episode, callback) },
            // 6. Extractor Vidsrc
            { invokeVidsrc(parsedData.imdbId, parsedData.season, parsedData.episode, callback) },
            // 7. Extractor Vidsrc.cc
            { invokeVidsrccc(parsedData.tmdbId, parsedData.imdbId, parsedData.season, parsedData.episode, subtitleCallback, callback) },
            // 8. Extractor Vidlink (WebView)
            { invokeVidlink(parsedData.tmdbId, parsedData.season, parsedData.episode, callback) },
            // 9. Extractor Vidfast (WebView)
            { invokeVidfast(parsedData.tmdbId, parsedData.season, parsedData.episode, subtitleCallback, callback) }
        )
        return true
    }

    data class LinkData(
        val links: List<String>,
        val tmdbId: String?,
        val imdbId: String?,
        val season: Int?,
        val episode: Int?,
        val type: String?,
        val title: String?,
        val year: Int?
    )
}
