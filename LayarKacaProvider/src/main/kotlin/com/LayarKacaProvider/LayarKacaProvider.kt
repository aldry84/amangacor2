package com.LayarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element

class LayarKacaProvider : MainAPI() {
    override var mainUrl = "https://tv8.lk21official.cc" 
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ========================================================================
    // MAIN PAGE
    // ========================================================================
    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Populer",
        "$mainUrl/most-watched/page/" to "Paling Banyak Ditonton",
        "$mainUrl/latest/page/" to "Terbaru",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/comedy/page/" to "Comedy",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/romance/page/" to "Romance",
        "$mainUrl/country/indonesia/page/" to "Indonesia"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        val home = document.select("div.grid-archive > div#grid-wrapper > div.row > div")
            .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h1.grid-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        val posterUrl = this.selectFirst("figure.grid-poster img")?.attr("src")
        val quality = this.selectFirst("span.quality")?.text()?.trim() ?: "HD"

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.search-item").mapNotNull {
            val titleElement = it.selectFirst("h3 a") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val href = titleElement.attr("href")
            val posterUrl = it.selectFirst("img")?.attr("src")
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    // ========================================================================
    // LOAD (KEMBALI KE LOGIKA AWAL YANG BERHASIL)
    // ========================================================================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        val poster = document.selectFirst("img.poster-image")?.attr("src")
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()
        
        // KEMBALI KE INT: Ambil rating sebagai String, lalu ambil angka depannya saja kalau perlu, atau parse Int
        // Misal "7.2" -> Diambil 7 atau dikali 10 jadi 72 biar aman masuk Int
        val ratingText = document.selectFirst("span.rating")?.text()?.trim()
        val rating = ratingText?.toDoubleOrNull()?.toInt() // Convert Double ke Int (7.5 -> 7) biar aman build

        val tags = document.select("div.gmr-movie-on a[rel=category tag]").map { it.text() }
        val trailer = document.selectFirst("a.fancybox-youtube")?.attr("href")

        val episodes = document.select("ul.episode-list li a").map {
            val epHref = it.attr("href")
            val epName = it.text().trim()
            Episode(epHref, epName) // Pakai cara lama yang simpel
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.rating = rating // Masuk sebagai Int?
                this.tags = tags
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.rating = rating // Masuk sebagai Int?
                this.tags = tags
                addTrailer(trailer)
            }
        }
    }

    // ========================================================================
    // LOAD LINKS (DENGAN LOG DEBUGGING)
    // ========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("LK21-DEBUG", "Load Link Start: $data")
        val document = app.get(data).document

        // 1. Cari Iframe
        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            Log.d("LK21-DEBUG", "Iframe Found: $src")
            loadExtractor(src, data, subtitleCallback, callback)
        }

        // 2. Cari Tombol Provider
        document.select("ul#loadProviders li a").forEach { linkElement ->
            var link = linkElement.attr("href")
            if (link.startsWith("//")) link = "https:$link"
            Log.d("LK21-DEBUG", "Provider Found: ${linkElement.text()} -> $link")
            loadExtractor(link, data, subtitleCallback, callback)
        }

        return true
    }
}
