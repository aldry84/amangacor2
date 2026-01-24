package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.*

class NgeFilmProvider : MainAPI() {
    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NGEFILM21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Helper untuk membersihkan URL poster jadi HD sesuai analisa log
    private fun String.toHighDef(): String {
        return this.replace(Regex("-\\d+x\\d+"), "")
    }

    override val mainPage = mainPageOf(
        "$mainUrl/year/2026/" to "Film Terbaru 2026",
        "$mainUrl/Genre/action/" to "Action",
        "$mainUrl/Genre/adventure/" to "Adventure",
        "$mainUrl/Genre/science-fiction/" to "Sci-Fi",
        "$mainUrl/country/indonesia/" to "Indonesia",
        "$mainUrl/country/korea/" to "Drama Korea",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + if (page > 1) "page/$page/" else "").document
        val home = doc.select("article").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst(".entry-title a")?.text() ?: return null
        val href = this.selectFirst(".entry-title a")?.attr("href") ?: return null
        val poster = this.selectFirst("img")?.attr("src")?.toHighDef()
        
        return if (href.contains("/eps/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = poster
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val doc = app.get(url).document
        return doc.select("article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val plot = doc.selectFirst(".entry-content p")?.text()
        
        // Ambil trailer dari log youtube tadi
        val trailer = doc.selectFirst(".gmr-trailer-popup")?.attr("href")

        if (url.contains("/tv/") || doc.selectFirst(".gmr-listseries") != null) {
            val episodes = doc.select(".gmr-listseries a.button").filter { 
                !it.text().contains("Pilih Episode") 
            }.map {
                Episode(
                    it.attr("href"),
                    it.text().trim(),
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.addTrailer(trailer)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.addTrailer(trailer)
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
        
        // Koleksi server dari tab player (Server 1-6)
        val servers = mutableListOf<String>()
        doc.select(".muvipro-player-tabs li a").forEach {
            val link = it.attr("href")
            if (link.startsWith("http")) servers.add(link)
            else servers.add(mainUrl + link)
        }

        // Jika tidak ada di tab, ambil iframe utama
        val mainIframe = doc.selectFirst(".gmr-embed-responsive iframe")?.attr("src")
        if (mainIframe != null) {
            loadExtractor(mainIframe, data, subtitleCallback, callback)
        }

        // Iterasi server lain (Server 2, 3, dst)
        servers.forEach { serverUrl ->
            val sDoc = app.get(serverUrl).document
            sDoc.select(".gmr-embed-responsive iframe").attr("src").let {
                if (it.isNotEmpty()) {
                    // Logic untuk bypass/load extractor pihak ketiga (Kraken, GdrivePlayer, dll)
                    loadExtractor(it, serverUrl, subtitleCallback, callback)
                }
            }
        }
        
        return true
    }
}
