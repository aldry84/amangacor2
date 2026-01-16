package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class LayarKacaProvider : MainAPI() {

    override var mainUrl = "https://tv7.lk21official.cc"
    private var seriesDomain = "https://tv3.nontondrama.my"
    private var searchApiUrl = "https://gudangvape.com"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/most-commented/page/" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/series/asian/page/" to "Film Asian Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).documentLarge
        val home = document.select("article figure").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        val rawHref = this.selectFirst("a")!!.attr("href")
        val href = fixUrl(rawHref) 
        
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get(
            "$searchApiUrl/search.php?s=$query",
            headers = mapOf(
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/"
            )
        ).text
        
        val results = mutableListOf<SearchResponse>()

        try {
            val root = JSONObject(res)
            if (root.has("data")) {
                val arr = root.getJSONArray("data")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val title = item.getString("title")
                    val slug = item.getString("slug")
                    val type = item.getString("type") 
                    
                    var posterUrl = item.optString("poster")
                    if (!posterUrl.startsWith("http")) {
                        posterUrl = "https://poster.lk21.party/wp-content/uploads/$posterUrl"
                    }

                    val itemUrl = if (type == "series") "$seriesDomain/$slug" else "$mainUrl/$slug"

                    if (type == "series") {
                        results.add(newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                            this.posterUrl = posterUrl
                        })
                    } else {
                        results.add(newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                            this.posterUrl = posterUrl
                        })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKacaSearch", "Error parsing JSON: ${e.message}")
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        var response = app.get(url)
        var document = response.documentLarge
        var finalUrl = response.url 
        
        val bodyText = document.body().text()
        if (bodyText.contains("dialihkan ke", ignoreCase = true) && bodyText.contains("Nontondrama", ignoreCase = true)) {
            val redirectLink = document.select("a").firstOrNull { 
                it.text().contains("Buka Sekarang", ignoreCase = true) ||
                it.attr("href").contains("nontondrama", ignoreCase = true)
            }?.attr("href")
            
            if (!redirectLink.isNullOrEmpty()) {
                finalUrl = fixUrl(redirectLink)
                document = app.get(finalUrl).documentLarge
            }
        }

        val baseurl = getBaseUrl(finalUrl)
        
        var title = document.selectFirst("div.movie-info h1")?.text()?.trim() 
            ?: document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: "Unknown Title"

        var poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders = mapOf("Referer" to baseurl)
        val description = document.selectFirst("div.meta-info")?.text()?.trim() 
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val tvType = if (finalUrl.contains("nontondrama") || document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            val json = document.selectFirst("script#season-data")?.data()
            if (!json.isNullOrEmpty()) {
                val root = JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        val slug = ep.getString("slug")
                        val href = fixUrl(slug)
                        episodes.add(newEpisode(href) {
                            this.name = "Episode ${ep.optInt("episode_no")}"
                            this.season = ep.optInt("s")
                            this.episode = ep.optInt("episode_no")
                        })
                    }
                }
            } else {
                document.select("ul.episodios li a").forEach { 
                    episodes.add(newEpisode(fixUrl(it.attr("href"))) {
                        this.name = it.text().trim()
                    })
                }
            }

            newTvSeriesLoadResponse(title, finalUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.score = Score.from10(rating)
            }
        } else {
            newMovieLoadResponse(title, finalUrl, TvType.Movie, finalUrl) {
                this.posterUrl = poster
                this.plot = description
                this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).documentLarge
        var playerNodes = document.select("ul#player-list > li")
        if (playerNodes.isEmpty()) playerNodes = document.select("div.player_nav ul li")

        playerNodes.map {
            fixUrl(it.select("a").attr("href"))
        }.amap {
            val iframeUrl = it.getIframe(referer = data)
            if(iframeUrl.isNotEmpty()) {
                loadExtractor(iframeUrl, getBaseUrl(it), subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun String.getIframe(referer: String): String {
        val response = app.get(this, referer = referer)
        var src = response.documentLarge.selectFirst("div.embed-container iframe")?.attr("src")

        if (src.isNullOrEmpty()) {
            val regex = """["'](https?://[^"']+)["']""".toRegex()
            src = regex.findAll(response.text).map { it.groupValues[1] }.firstOrNull { 
                it.contains("embed") || it.contains("player") 
            }
        }
        return fixUrl(src ?: "")
    }

    private fun Element.getImageAttr(): String = if (this.hasAttr("data-src")) this.attr("data-src") else this.attr("src")
    fun getBaseUrl(url: String?): String = try { URI(url).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { "" }
}
