package com.JuraganFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JuraganFilm : MainAPI() {

    // Konfigurasi Utama
    override var mainUrl = "https://tv41.juragan.film"
    override var name = "JuraganFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Header Standar (Meniru Browser HP Android)
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // =========================================================================
    // 1. HALAMAN UTAMA (Home)
    // =========================================================================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, headers = commonHeaders).document
        
        // Mengambil daftar film dari <div id="gmr-main-load">
        val homeItems = document.select("div#gmr-main-load article").mapNotNull {
            toSearchResult(it)
        }

        return newHomePageResponse(
            list = HomePageList(
                name = "Film Terbaru",
                list = homeItems,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    // =========================================================================
    // 2. PENCARIAN (Search)
    // =========================================================================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = commonHeaders).document

        // Di halaman search, elemen film biasanya punya class 'item' atau 'search-item'
        return document.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    // Fungsi Pembantu: Mengubah Elemen HTML menjadi Data Film Cloudstream
    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("div.item-article a") ?: return null
        
        // Membersihkan judul dari kata-kata seperti "Nonton", "Film", dll supaya rapi
        val rawTitle = titleElement.text()
        val title = rawTitle.replace(Regex("(?i)Nonton\\s+(Film\\s+)?"), "").trim()
        
        val href = titleElement.attr("href")

        // Mengambil Poster (Cek data-src untuk lazy loading)
        val posterElement = element.selectFirst("div.content-thumbnail img")
        val posterUrl = posterElement?.let { img ->
            img.attr("data-src").ifEmpty { img.attr("src") }
        }

        // Cek apakah ini Serial TV (ada teks EPS atau Season)
        val isSeries = element.text().contains("EPS", ignoreCase = true) || 
                       rawTitle.contains("Season", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    // =========================================================================
    // 3. MEMUAT DETAIL (Load)
    // =========================================================================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders).document

        // Judul
        val rawTitle = document.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val title = rawTitle.replace(Regex("(?i)Nonton\\s+(Film\\s+)?"), "").trim()
        
        // Poster HD
        val poster = document.selectFirst("div.gmr-poster img")?.let { img ->
             img.attr("data-src").ifEmpty { img.attr("src") }
        } ?: document.selectFirst("img.wp-post-image")?.attr("src")

        // Deskripsi & Tahun
        val description = document.select("div.entry-content p").text()
        val year = document.select("span.year").text().filter { it.isDigit() }.toIntOrNull()

        // Cek Episode (Logika Khusus Series)
        val episodes = mutableListOf<Episode>()
        val episodeElements = document.select("div.gmr-listseries a")
        
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { ep ->
                val epTitle = ep.text()
                val epUrl = ep.attr("href")
                episodes.add(newEpisode(epUrl) {
                    this.name = epTitle
                })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    // =========================================================================
    // 4. MENGAMBIL LINK VIDEO (LoadLinks)
    // =========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document

        // Loop semua iframe yang ada di halaman
        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            
            // Perbaiki URL jika dimulai dengan // atau /
            if (src.startsWith("//")) src = "https:$src"
            if (src.startsWith("/")) src = "$mainUrl$src"

            // Filter URL sampah (iklan/tracking)
            if (src.contains("facebook") || src.contains("sbani") || src.contains("histats")) {
                return@forEach
            }

            // LOGIKA PENTING: Menangani Cloudbeta/JuraganFilm Player
            // Kita kirimkan referer halaman utama agar server tidak menolak (Error 400)
            loadExtractor(
                url = src,
                referer = "$mainUrl/", // Ini kuncinya!
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
        
        return true
    }
}
