package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {
    override var mainUrl = "https://new31.ngefilm.site" 
    override var name = "NgeFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Terbaru",
        "$mainUrl/Genre/action/page/" to "Action",
        "$mainUrl/Genre/horror/page/" to "Horror",
        "$mainUrl/Genre/drama/page/" to "Drama",
        "$mainUrl/Genre/comedy/page/" to "Comedy",
        "$mainUrl/country/indonesia/page/" to "Indonesia",
        "$mainUrl/country/korea/page/" to "Drama Korea"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data.removeSuffix("page/") else "${request.data}$page/"
        val document = app.get(url, headers = commonHeaders).document
        val home = document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = this.selectFirst("img.attachment-medium")?.let { 
            it.attr("data-src").ifEmpty { it.attr("src") } 
        }
        val quality = this.select("div.gmr-quality-item a").text() 
        val isSeries = href.contains("/tv/") || this.select(".gmr-numbeps").isNotEmpty()

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url, headers = commonHeaders).document
        return document.select("article.item-infinite").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = commonHeaders).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        
        var poster = document.selectFirst("div.gmr-poster img")?.let { 
            it.attr("data-src").ifEmpty { it.attr("src") }
        }
        if (poster.isNullOrEmpty()) {
            poster = document.selectFirst("meta[property=og:image]")?.attr("content")
        }

        val plot = document.selectFirst("div.entry-content p")?.text()
        val year = Regex("\\d{4}").find(document.select("span.year").text() ?: "")?.value?.toIntOrNull()
        
        val durationText = document.select("div.gmr-duration-item").text().trim()
        val durationMin = Regex("(\\d+)").find(durationText)?.groupValues?.get(1)?.toIntOrNull()

        val isSeries = url.contains("/tv/") || document.select("div.gmr-listseries").isNotEmpty()

        if (isSeries) {
            val episodes = document.select("div.gmr-listseries a").map {
                val epsName = it.text()
                val epsNum = Regex("Ep\\.?\\s*(\\d+)").find(epsName)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(it.attr("href")) {
                    this.name = epsName
                    this.episode = epsNum
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.duration = durationMin 
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.duration = durationMin
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document
        val collectedLinks = mutableListOf<String>()

        // 1. Cari di Iframe (Prioritas)
        document.select("iframe").forEach { 
            val src = it.attr("src")
            if(src.isNotBlank()) collectedLinks.add(fixUrl(src))
        }

        // 2. Cari di Tombol Server (Backup)
        document.select("ul.gmr-player-nav li a").forEach { 
            val href = it.attr("href")
            if(href.isNotBlank()) collectedLinks.add(fixUrl(href))
        }

        collectedLinks.distinct().forEach { rawUrl ->
            var finalUrl = rawUrl
            
            // Unshorten link pendek
            if (finalUrl.contains("short.icu") || finalUrl.contains("hglink.to")) {
                try {
                    finalUrl = app.get(finalUrl, headers = commonHeaders).url
                } catch (e: Exception) { }
            }

            // Routing ke Extractor
            // Cek semua kemungkinan domain RpmLive
            if (finalUrl.contains("rpmlive.online") || finalUrl.contains("playerngefilm21")) {
                RpmLive().getStreamUrl(finalUrl, callback)
            } else if (finalUrl.isNotBlank() && !finalUrl.contains("facebook") && !finalUrl.contains("twitter")) {
                loadExtractor(finalUrl, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
