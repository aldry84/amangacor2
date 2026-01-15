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
        "$mainUrl/populer/page/" to "Top Bulan Ini",
        "$mainUrl/latest/page/" to "Film Terbaru",
        "$mainUrl/top-series-today/page/" to "Series Hari Ini",
        "$mainUrl/latest-series/page/" to "Series Terbaru",
        "$mainUrl/nonton-bareng-keluarga/page/" to "Nobar Keluarga",
        "$mainUrl/genre/romance/page/" to "Romantis",
        "$mainUrl/country/thailand/page/" to "Thailand"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2 a")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrl(this.selectFirst("img")?.getImageAttr() ?: "")

        return if (href.contains("/series/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$searchApiUrl/search.php?s=${query}"
        val response = app.get(url).text
        val document = org.jsoup.Jsoup.parse(response)

        return document.select("article.item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val responseText = app.get(url).text
        val document = org.jsoup.Jsoup.parse(responseText)

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = fixUrl(document.selectFirst("img.ant-img")?.getImageAttr() ?: "")
        val tags = document.select("ul.genre li a").map { it.text() }
        val year = document.selectFirst("ul.info li:contains(Year:) a")?.text()?.toIntOrNull()
        val tvType = if (url.contains("/series/")) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("blockquote")?.text()?.trim()
        val trailer = document.selectFirst("a.btn-trailer")?.attr("href")

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("ul.episode-list li a").map {
                val href = fixUrl(it.attr("href"))
                val episodeName = it.text().trim()
                Episode(href, episodeName)
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        // Ambil link iframe dari tombol server atau script
        val iframeUrl = getSafeIframe(doc, data)
        
        if (iframeUrl.isNotEmpty()) {
            // Panggil Router PlayerIframe yang sudah kita buat di Extractors.kt
            loadExtractor(iframeUrl, data, subtitleCallback, callback)
        }

        return true
    }

    private suspend fun getSafeIframe(document: org.jsoup.nodes.Document, responseText: String): String {
        var src = document.selectFirst("iframe[src*=playeriframe]")?.attr("src")
            ?: document.selectFirst("iframe[src*=hownetwork]")?.attr("src")
            ?: document.selectFirst("iframe[src*=f16px]")?.attr("src")

        if (src.isNullOrEmpty()) {
            src = document.selectFirst("iframe[src^=http]")?.attr("src")
        }

        if (src.isNullOrEmpty()) {
            val regex = """["'](https?://[^"']+)["']""".toRegex()
            val foundLinks = regex.findAll(responseText).map { it.groupValues[1] }.toList()
            
            // PERBAIKAN: Hapus filter yang membuang .png karena Turbovid menggunakannya
            src = foundLinks.firstOrNull { link -> 
                !link.contains(".js") && 
                !link.contains(".css") && 
                !link.contains(".jpg") &&
                (link.contains("playeriframe") || link.contains("embed") || link.contains("player") || link.contains("streaming") || link.contains("hownetwork"))
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

    fun fixUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        return url
    }
}
