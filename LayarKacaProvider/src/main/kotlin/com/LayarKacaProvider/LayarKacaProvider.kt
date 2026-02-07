package com.LayarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element

class LayarKacaProvider : MainAPI() {
    // Domain bisa berubah sewaktu-waktu, sesuaikan dengan yang aktif
    override var mainUrl = "https://tv8.lk21official.cc" 
    override var name = "LayarKaca21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ========================================================================
    // MAIN PAGE & SEARCH
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
        
        // Cek Quality (HD, CAM, dll)
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
    // LOAD DETAILS (FIXED DEPRECATION)
    // ========================================================================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        val poster = document.selectFirst("img.poster-image")?.attr("src")
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim()
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()
        
        // FIX 1: Rating sekarang menggunakan Double, bukan Int
        val rating = document.selectFirst("span.rating")?.text()?.trim()?.toDoubleOrNull()
        
        val tags = document.select("div.gmr-movie-on a[rel=category tag]").map { it.text() }
        val trailer = document.selectFirst("a.fancybox-youtube")?.attr("href")

        // FIX 2: Episode menggunakan newEpisode builder
        val episodes = document.select("ul.episode-list li a").map {
            val epHref = it.attr("href")
            val epName = it.text().trim()
            newEpisode(epHref) {
                this.name = epName
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.rating = rating // Assign rating Double
                this.tags = tags
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.rating = rating // Assign rating Double
                this.tags = tags
                addTrailer(trailer)
            }
        }
    }

    // ========================================================================
    // LOAD LINKS (MASIH ADA DEBUGGING UNTUK F16)
    // ========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("LK21-DEBUG", "=== MEMUAT HALAMAN FILM: $data ===")
        val document = app.get(data).document

        // 1. Log Semua Iframe
        Log.d("LK21-DEBUG", "--- MENCARI IFRAME ---")
        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            Log.d("LK21-DEBUG", "Iframe Ditemukan: $src")
            loadExtractor(src, data, subtitleCallback, callback)
        }

        // 2. Log Tombol Provider
        Log.d("LK21-DEBUG", "--- MENCARI TOMBOL PROVIDER ---")
        val providers = document.select("ul#loadProviders li a")
        if (providers.isEmpty()) {
            Log.d("LK21-DEBUG", "TIDAK ADA TOMBOL PROVIDER DITEMUKAN (Mungkin pake ajax?)")
        }

        providers.forEach { linkElement ->
            var link = linkElement.attr("href")
            val name = linkElement.text()
            
            if (link.startsWith("//")) link = "https:$link"
            
            Log.d("LK21-DEBUG", "Tombol Provider: [$name] -> $link")
            
            // Panggil Extractor
            loadExtractor(link, data, subtitleCallback, callback)
        }

        Log.d("LK21-DEBUG", "=== SELESAI LOAD LINKS ===")
        return true
    }
}
