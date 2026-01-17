package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class LayarKacaProvider : MainAPI() {

    override var mainUrl = "https://tv7.lk21official.cc"
    private var seriesDomain = "https://tv3.nontondrama.my"
    private var searchApiUrl = "https://gudangvape.com"

    override var name = "LayarKaca2"
    
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Top Bulan Ini",
        "$mainUrl/latest/page/" to "Film Terbaru",
        "$mainUrl/top-series-today/page/" to "Series Hari Ini",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/nonton-bareng-keluarga/page/" to "Nobar Keluarga",
        "$mainUrl/genre/romance/page/" to "Romantis",
        "$mainUrl/country/thailand/page/" to "Thailand"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data + page
        val response = app.get(url)
        val document = response.document
        val items = document.select("article.post-item").mapNotNull {
            val title = it.selectFirst("h2.entry-title a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("h2.entry-title a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.getImageAttr()
            val quality = it.selectFirst("span.quality")?.text()
            
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = getQualityFromString(quality)
            }
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$searchApiUrl/search.php?s=${query}"
        val response = app.get(url).text
        val json = try {
            JSONObject("{\"results\": $response}").getJSONArray("results")
        } catch (e: Exception) {
            return emptyList()
        }

        val results = ArrayList<SearchResponse>()
        for (i in 0 until json.length()) {
            val item = json.getJSONObject(i)
            val title = item.getString("title")
            val id = item.getString("id")
            val href = "$mainUrl/$id"
            val poster = item.optString("poster")
            val type = if (item.optString("type") == "series") TvType.TvSeries else TvType.Movie
            
            results.add(newMovieSearchResponse(title, href, type) {
                this.posterUrl = poster
            })
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url)
        val document = response.document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        val poster = document.selectFirst("div.thumb img")?.getImageAttr()
        val desc = document.selectFirst("div.entry-content p")?.text()?.trim()
        val year = document.selectFirst("span.year")?.text()?.trim()?.toIntOrNull()
        
        // CATATAN: Rating & Trailer dihapus sementara karena menyebabkan Error Build di server GitHub.
        // Fokus kita sekarang adalah agar Aplikasi BISA DI-BUILD dan VIDEO BISA DIPUTAR.

        val tvType = if (url.contains("series") || document.select("div.gmr-listseries").isNotEmpty()) 
            TvType.TvSeries else TvType.Movie

        val recommendations = document.select("div.related-post article").mapNotNull {
            val recTitle = it.selectFirst("h2.entry-title a")?.text() ?: return@mapNotNull null
            val recHref = it.selectFirst("h2.entry-title a")?.attr("href") ?: return@mapNotNull null
            val recPoster = it.selectFirst("img")?.getImageAttr()
            newMovieSearchResponse(recTitle, recHref, TvType.Movie) {
                this.posterUrl = recPoster
            }
        }

        if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.gmr-listseries a").map {
                val epHref = it.attr("href")
                val epTitle = it.text()
                val episodeNum = epTitle.filter { char -> char.isDigit() }.toIntOrNull()
                
                newEpisode(epHref) {
                    this.name = epTitle
                    this.episode = episodeNum
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = desc
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
        val iframe = data.getIframe(data)
        return loadExtractor(iframe, data, subtitleCallback, callback)
    }

    private suspend fun String.getIframe(referer: String): String {
        val response = app.get(this, referer = referer)
        val document = response.documentLarge
        val responseText = response.text

        var src = document.selectFirst("div.embed-container iframe")?.attr("src")
        if (src.isNullOrEmpty()) {
            src = document.selectFirst("iframe[src^=http]")?.attr("src")
        }

        if (src.isNullOrEmpty()) {
            val regex = """["'](https?://[^"']+)["']""".toRegex()
            val foundLinks = regex.findAll(responseText).map { it.groupValues[1] }.toList()
            
            src = foundLinks.firstOrNull { link -> 
                !link.contains(".js") && 
                !link.contains(".css") && 
                !link.contains(".png") && 
                !link.contains(".jpg") &&
                (link.contains("embed") || 
                 link.contains("player") || 
                 link.contains("streaming") || 
                 link.contains("cast.box") || 
                 link.contains("f16px") ||    
                 link.contains("turbovid"))   
            }
        }

        if (src.isNullOrEmpty()) {
             if (this.contains("cast.box") || this.contains("turbovid") || this.contains("f16px")) {
                return this
            }
        }

        return fixUrl(src ?: "")
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("src") -> this.attr("src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> this.attr("src")
        }
    }
}
