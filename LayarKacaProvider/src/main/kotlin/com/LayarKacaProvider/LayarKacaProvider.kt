package com.layarKacaProvide

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
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).documentLarge
        val home = document.select("article figure").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        
        return if (type == TvType.TvSeries) {
            newAnimeSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$searchApiUrl/search.php?s=$query", headers = mapOf("Origin" to mainUrl)).text
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
                    val posterUrl = "https://poster.lk21.party/wp-content/uploads/${item.optString("poster")}"
                    val itemUrl = if (type == "series") "$seriesDomain/$slug" else "$mainUrl/$slug"
                    val searchType = if (type == "series") TvType.TvSeries else TvType.Movie
                    
                    if (searchType == TvType.TvSeries) {
                        results.add(newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) { this.posterUrl = posterUrl })
                    } else {
                        results.add(newMovieSearchResponse(title, itemUrl, TvType.Movie) { this.posterUrl = posterUrl })
                    }
                }
            }
        } catch (e: Exception) { Log.e("LayarKaca", e.message ?: "Error") }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        var response = app.get(url)
        var document = response.documentLarge
        var finalUrl = response.url 
        
        // Cek Redirect Manual (Nontondrama)
        if (document.body().text().contains("dialihkan ke", ignoreCase = true)) {
            val redirectLink = document.select("a[href*=nontondrama]").attr("href")
            if (redirectLink.isNotEmpty()) {
                finalUrl = fixUrl(redirectLink)
                document = app.get(finalUrl).documentLarge
            }
        }

        val title = document.selectFirst("div.movie-info h1, h1.entry-title")?.text()?.trim() ?: "Unknown"
        var poster = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val description = document.selectFirst("div.meta-info, blockquote")?.text()?.trim()
        val year = Regex("\\d, (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull()
        
        val recommendations = document.select("li.slider article").mapNotNull { it.toSearchResult() }

        val hasSeasonData = document.selectFirst("#season-data") != null
        val isSeries = finalUrl.contains("nontondrama") || hasSeasonData

        if (isSeries) {
            val episodes = mutableListOf<Episode>()
            val json = document.selectFirst("script#season-data")?.data()
            if (!json.isNullOrEmpty()) {
                val root = JSONObject(json)
                root.keys().forEach { s ->
                    val arr = root.getJSONArray(s)
                    for (i in 0 until arr.length()) {
                        val ep = arr.getJSONObject(i)
                        val href = fixUrl(ep.getString("slug"))
                        episodes.add(newEpisode(href) {
                            this.name = "Episode ${ep.optInt("episode_no")}"
                            this.season = ep.optInt("s")
                            this.episode = ep.optInt("episode_no")
                        })
                    }
                }
            } else {
                document.select("ul.episodios li a").forEach {
                     episodes.add(newEpisode(fixUrl(it.attr("href"))) { this.name = it.text() })
                }
            }
            return newTvSeriesLoadResponse(title, finalUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, finalUrl, TvType.Movie, finalUrl) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.recommendations = recommendations
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
        val playerNodes = document.select("ul#player-list > li a, div.player_nav ul li a")

        playerNodes.amap { linkElement ->
            val serverName = linkElement.text().trim()
            val rawHref = linkElement.attr("href")
            val href = fixUrl(rawHref)

            Log.d("LayarKaca", "Processing Server: $serverName -> $href")

            var iframeUrl = href.getIframe(referer = data)
            
            // Penanganan Redirect (HYDRAX / SHORT.ICU)
            if (iframeUrl.contains("short.icu") || iframeUrl.contains("hydrax")) {
                iframeUrl = resolveRedirect(iframeUrl)
                Log.d("LayarKaca", "Resolved Redirect ($serverName): $iframeUrl")
            }

            if (iframeUrl.isNotEmpty()) {
                loadExtractor(iframeUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }

    private suspend fun String.getIframe(referer: String): String {
        if (this.isEmpty()) return ""
        try {
            val response = app.get(this, referer = referer)
            val doc = response.documentLarge
            
            var src = doc.select("iframe").attr("src")
            
            if (src.isEmpty()) {
                // Regex mencakup domain-domain nakal (f16px, emturbovid)
                val regex = """["'](https?://[^"']*(?:turbovid|hydrax|short|embed|player|watch|hownetwork|cloud|dood|mixdrop|f16px|emturbovid)[^"']*)["']""".toRegex()
                src = regex.find(response.text)?.groupValues?.get(1) ?: ""
            }
            
            if (src.isEmpty() && response.url.contains("hownetwork")) return response.url

            return fixUrl(src)
        } catch (e: Exception) { return "" }
    }

    private suspend fun resolveRedirect(url: String): String {
        return try {
            val response = app.get(url, allowRedirects = false)
            if (response.code == 301 || response.code == 302) {
                response.headers["Location"] ?: url
            } else {
                url
            }
        } catch (e: Exception) { url }
    }

    private fun Element.getImageAttr(): String = 
        if (this.hasAttr("data-src")) this.attr("data-src") else this.attr("src")
}
