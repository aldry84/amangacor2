package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {
    override var mainUrl = "https://ngefilm21.pw"
    override var name = "NgeFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/trending/" to "Trending",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/horror/" to "Horror",
        "$mainUrl/type/movie/" to "Movies",
        "$mainUrl/type/tv/" to "TV Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data}page/$page/"
        val document = app.get(url).document
        val home = document.select("article.item-infinite").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = this.selectFirst("h2.entry-title a")?.attr("href") ?: return null
        val posterUrl = this.selectFirst("img.attachment-medium")?.attr("src")

        return if (this.select(".gmr-numbereps").isNotEmpty()) {
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
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.item-infinite").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst("div.gmr-poster img")?.attr("src")
        val plot = document.selectFirst("div.entry-content p")?.text()
        val year = document.selectFirst("div.gmr-moviedata")?.text()?.let {
            Regex("\\d{4}").find(it)?.value?.toIntOrNull()
        }

        val tvType = if (url.contains("/tv/")) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.gmr-listseries a").map {
                newEpisode(it.attr("href")) {
                    this.name = it.text()
                }
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Ambil semua iframe video
        document.select("div.gmr-embed-responsive iframe").mapNotNull { 
            it.attr("src") 
        }.forEach { rawUrl ->
            var finalUrl = rawUrl

            // A. Tahap Unshorten
            if (finalUrl.contains("short.icu") || finalUrl.contains("hglink.to")) {
                finalUrl = app.get(finalUrl).url
            }

            // B. Routing Server
            if (finalUrl.contains("rpmlive.online")) {
                // Gunakan Extractor Kustom di Extractors.kt
                RpmLive().getStreamUrl(finalUrl, callback)
            } else {
                // Gunakan extractor bawaan (Abyss, Kraken, Hxfile, dll)
                loadExtractor(finalUrl, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
