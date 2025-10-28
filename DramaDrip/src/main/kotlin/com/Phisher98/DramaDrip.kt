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
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.runBlocking
import org.jsoup.nodes.Element

// CATATAN: DUMMY FUNCTIONS YANG MENYEBABKAN KONFLIK DIHAPUS DARI SINI.
// Fungsi cinematickitloadBypass, cinematickitBypass, dan bypassHrefli
// diasumsikan sudah ada di file Utils.kt dan dideklarasikan sebagai 'suspend fun'.

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

        // Logika Score untuk Daftar Film 
        val scoreElementText = this.selectFirst(".entry-content p")?.text() ?: ""
        val scoreValue: Int? = Regex("""Rating:\s*(\d+)(?:\.\d+)?%?""")
            .find(scoreElementText)?.groupValues?.getOrNull(1)?.toIntOrNull()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = scoreValue?.let { Score.from10(it.toString()) } 
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        val results = document.select("article").mapNotNull {
            it.toSearchResult()
        }
        return results
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        var imdbId: String? = null
        var tmdbId: String? = null
        var tmdbType: String? = null
        var rating: Int? = null 

        // LOGIKA PENGAMBILAN RATING (Halaman Detail)
        document.select("div.content-section > *").forEach { element ->
            val text = element.text()
            if (rating == null && text.contains("Rating", ignoreCase = true)) {
                 rating = Regex("""(\d+)(?:\.\d+)?%?$""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
        }

        if (rating == null) {
            document.select("div.su-spoiler-content ul.wp-block-list > li").forEach { li ->
                val text = li.text()
                if (imdbId == null && "imdb.com/title/tt" in text) {
                    imdbId = Regex("tt\\d+").find(text)?.value
                }
                
                if (rating == null && "Rating" in text) {
                    rating = Regex("""(\d+)(?:\.\d+)?%?$""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                }

                if (tmdbId == null && tmdbType == null && "themoviedb.org" in text) {
                    Regex("/(movie|tv)/(\\d+)").find(text)?.let { match ->
                        tmdbType = match.groupValues[1] 
                        tmdbId = match.groupValues[2]   
                    }
                }
            }
        }
        // AKHIR LOGIKA PENGAMBILAN RATING

        val tvType = when (true) {
            (tmdbType?.contains("Movie", ignoreCase = true) == true) -> TvType.Movie
            else -> TvType.TvSeries
        }

        val image = document.select("meta[property=og:image]").attr("content")
        val title = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringBefore("(")?.trim().toString()
        val tags = document.select("div.mt-2 span.badge").map { it.text() }
        val year = document.selectFirst("div.wp-block-column > h2.wp-block-heading")?.text()
            ?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val descriptions = document.selectFirst("div.content-section p.mt-4")?.text()?.trim()
        val typeset = if (tvType == TvType.TvSeries) "series" else "movie"
        val responseData = if (tmdbId?.isNotEmpty() == true) {
            val jsonResponse = app.get("$cinemeta_url/$typeset/$imdbId.json").text
            if (jsonResponse.isNotEmpty() && jsonResponse.startsWith("{")) {
                val gson = Gson()
                val parsed = gson.fromJson(jsonResponse, ResponseData::class.java)
                rating = rating ?: parsed.meta?.imdbRating?.toIntOrNull()
                parsed
            } else null
        } else null
        var cast: List<String> = emptyList()

        var background: String = image
        var description: String? = null
        if (responseData != null) {
            description = responseData.meta?.description ?: descriptions
            cast = responseData.meta?.cast ?: emptyList()
            background = responseData.meta?.background ?: image
        }


        val hrefs: List<String> = document.select("div.wp-block-button > a")
            .mapNotNull { linkElement ->
                val link = linkElement.attr("href")
                // MENGGUNAKAN FUNGSI SUSPEND ASLI DARI Utils.kt
                val actual=cinematickitloadBypass(link) ?: return@mapNotNull null 
                val page = app.get(actual).document
                page.select("div.wp-block-button.movie_btn a")
                    .eachAttr("href")
            }.flatten()

        val trailer = document.selectFirst("div.wp-block-embed__wrapper > iframe")?.attr("src")

        val recommendations =
            document.select("div.entry-related-inner-content article").mapNotNull {
                val recName = it.select("h3").text().substringAfter("Download")
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
                                // MENGGUNAKAN FUNGSI SUSPEND ASLI DARI Utils.kt
                                val rawqualityPageLink=if (qualityPageLink.contains("modpro")) qualityPageLink else cinematickitloadBypass(qualityPageLink) ?: ""
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
                            } catch (_: Exception) {
                                Log.e("EpisodeFetch", "Failed to load or parse $qualityPageLink")
                            }
                        }
                    }
                }
            }

            val finalEpisodes = tvSeriesEpisodes.map { (seasonEpisode, links) ->
                val (season, epNo) = seasonEpisode
                val info =
                    responseData?.meta?.videos?.find { it.season == season && it.episode == epNo }

                newEpisode(links.distinct().toJson()) {
                    this.name = info?.name ?: "Episode $epNo"
                    this.posterUrl = info?.thumbnail
                    this.season = season
                    this.episode = epNo
                    this.description = info?.overview
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, finalEpisodes) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
                this.score = rating?.let { Score.from10(it.toString()) }
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, hrefs) {
                this.backgroundPosterUrl = background
                this.year = year
                this.plot = description
                this.tags = tags
                this.recommendations = recommendations
                addTrailer(trailer)
                addActors(cast)
                addImdbId(imdbId)
                addTMDbId(tmdbId)
                this.score = rating?.let { Score.from10(it.toString()) }
            }
        }
    }

    // Fungsi placeholder untuk bypass MovieBox.
    private fun bypassMoviebox(link: String): String? {
        return link 
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
        
        // SUMBER VIDEO/EXTRACTOR LINKS
        for (link in links) {
            try {
                // MENGGUNAKAN SEMUA BYPASS SUSPEND DARI Utils.kt + MOVIEBOX
                val finalLink = when {
                    "moviebox.ph" in link || "inmoviebox.com" in link -> bypassMoviebox(link)
                    "safelink=" in link -> cinematickitBypass(link)
                    "unblockedgames" in link -> bypassHrefli(link) 
                    "examzculture" in link -> bypassHrefli(link) 
                    else -> link
                }

                if (finalLink != null) {
                    Log.d("LoadLinks", "Memuat sumber video dari: $finalLink")
                    loadExtractor(finalLink, subtitleCallback, callback)
                } else {
                    Log.w("LoadLinks", "Bypass returned null for link: $link")
                }
            } catch (_: Exception) {
                Log.e("LoadLinks", "Failed to load link: $link")
            }
        }

        return true
    }
}
