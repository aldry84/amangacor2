package com.Idlixku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.fasterxml.jackson.annotation.JsonProperty
import org.jsoup.nodes.Element

class Idlixku : MainAPI() {
    override var mainUrl = "https://tv12.idlixku.com"
    override var name = "Idlixku"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Anime)

    private val standardHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    // ================== 1. HOME PAGE ==================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, headers = standardHeaders).document
        val homePageList = ArrayList<HomePageList>()

        val featured = document.select(".items.featured article").mapNotNull { toSearchResult(it) }
        if (featured.isNotEmpty()) homePageList.add(HomePageList("Featured", featured))

        val movies = document.select("#dt-movies article").mapNotNull { toSearchResult(it) }
        if (movies.isNotEmpty()) homePageList.add(HomePageList("Film Terbaru", movies))

        val tvSeries = document.select("#dt-tvshows article").mapNotNull { toSearchResult(it) }
        if (tvSeries.isNotEmpty()) homePageList.add(HomePageList("Serial TV Terbaru", tvSeries))

        val drakor = document.select("#genre_drama-korea article").mapNotNull { toSearchResult(it) }
        if (drakor.isNotEmpty()) homePageList.add(HomePageList("Drama Korea", drakor))

        val anime = document.select("#genre_anime article").mapNotNull { toSearchResult(it) }
        if (anime.isNotEmpty()) homePageList.add(HomePageList("Anime", anime))

        return newHomePageResponse(homePageList)
    }

    // ================== 2. SEARCH ==================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = standardHeaders).document

        return document.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst(".data h3 a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = element.selectFirst(".poster img")?.attr("src")
        val quality = element.selectFirst(".quality")?.text()

        val isTv = href.contains("/tvseries/") || element.hasClass("tvshows")

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality ?: "")
            }
        }
    }

    // ================== 3. LOAD DETAIL ==================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = standardHeaders).document

        val title = document.selectFirst(".data h1")?.text()?.trim() ?: "Unknown Title"
        val poster = document.selectFirst(".poster img")?.attr("src")
        val description = document.selectFirst("#info .wp-content p")?.text()
            ?: document.selectFirst("center p")?.text() ?: ""
        
        val year = document.selectFirst(".extra .date")?.text()?.takeLast(4)?.toIntOrNull()
        
        // FIX ERROR 1: Menggunakan Score.from10 (Sesuai Adimoviebox)
        // Rating di idlix biasanya string "9.8", Score.from10 menanganinya otomatis
        val ratingText = document.selectFirst(".dt_rating_vgs")?.text()
        val scoreData = Score.from10(ratingText)

        val recommendations = document.select("#single_relacionados article").mapNotNull {
            toSearchResult(it)
        }

        val trailerUrl = document.selectFirst("#trailer iframe")?.attr("src")

        val episodeElements = document.select("#seasons .episodios li")

        if (episodeElements.isNotEmpty()) {
            val episodes = ArrayList<Episode>()
            episodeElements.forEach {
                val link = it.selectFirst(".episodiotitle a")?.attr("href") ?: return@forEach
                val name = it.selectFirst(".episodiotitle a")?.text() ?: "Episode"
                val date = it.selectFirst(".date")?.text()
                val posterEp = it.selectFirst(".imagen img")?.attr("src")

                val numerando = it.selectFirst(".numerando")?.text() ?: "1-1"
                val seasonNum = numerando.split("-").firstOrNull()?.trim()?.toIntOrNull() ?: 1
                val episodeNum = numerando.split("-").lastOrNull()?.trim()?.toIntOrNull() ?: 1

                episodes.add(
                    newEpisode(link) {
                        this.name = name
                        this.season = seasonNum
                        this.episode = episodeNum
                        this.posterUrl = posterEp
                        this.date = date
                    }
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = scoreData // Set Score
                this.recommendations = recommendations
                addTrailer(trailerUrl)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.score = scoreData // Set Score
                this.recommendations = recommendations
                addTrailer(trailerUrl)
            }
        }
    }

    // ================== 4. LOAD LINKS ==================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = standardHeaders).document

        document.select("li.dooplay_player_option").map { element ->
            val postId = element.attr("data-post")
            val nume = element.attr("data-nume")
            val type = element.attr("data-type")

            val formData = mapOf(
                "action" to "dooplay_player_ajax",
                "post" to postId,
                "nume" to nume,
                "type" to type
            )

            val response = app.post(
                "$mainUrl/wp-admin/admin-ajax.php",
                data = formData,
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                referer = data
            ).parsedSafe<DooPlayResponse>()

            val embedUrl = response?.embed_url ?: return@map

            when {
                embedUrl.contains("jeniusplay.com") -> {
                    invokeJeniusExtractor(embedUrl, callback)
                }
                else -> {
                    // FIX: Urutan callback yang benar untuk loadExtractor
                    loadExtractor(embedUrl, subtitleCallback, callback)
                }
            }
        }
        return true
    }

    // ================== INTERNAL EXTRACTOR ==================
    private suspend fun invokeJeniusExtractor(url: String, callback: (ExtractorLink) -> Unit) {
        try {
            val videoId = url.substringAfter("/video/")
            val domain = "https://jeniusplay.com"

            val jsonResponse = app.post(
                "$domain/player/index.php?data=$videoId&do=getVideo",
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to url
                ),
                data = mapOf("hash" to videoId, "r" to "")
            ).parsedSafe<JeniusResponse>()

            val playlistUrl = jsonResponse?.videoSource ?: return

            // FIX ERROR 2 & 3 & 4: Menggunakan Lambda Builder Pattern
            // Sesuai dengan Adimoviebox.kt
            callback.invoke(
                newExtractorLink(
                    source = "JeniusPlay",
                    name = "JeniusPlay (Auto)",
                    url = playlistUrl,
                    type = INFER_TYPE
                ) {
                    this.referer = domain
                    this.quality = Qualities.Unknown.value
                    this.isM3u8 = true
                }
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class DooPlayResponse(
        @JsonProperty("embed_url") val embed_url: String?,
        @JsonProperty("type") val type: String?
    )

    data class JeniusResponse(
        @JsonProperty("videoSource") val videoSource: String?,
        @JsonProperty("securedLink") val securedLink: String?
    )
}
