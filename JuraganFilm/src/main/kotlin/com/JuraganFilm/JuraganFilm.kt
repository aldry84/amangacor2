package com.JuraganFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JuraganFilm : MainAPI() {

    // URL Utama website
    override var mainUrl = "https://tv41.juragan.film"
    override var name = "JuraganFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Header agar tidak diblokir (Sesuai screenshot header request kamu)
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
        
        // Target: <div id="gmr-main-load"> berisi daftar <article>
        // Sesuai screenshot elemen
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

        // Di halaman search, kita cari elemen article dengan class 'item'
        return document.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    // =========================================================================
    // FUNGSI PENGOLAH DATA (HTML -> Cloudstream)
    // =========================================================================
    private fun toSearchResult(element: Element): SearchResponse? {
        // 1. Ambil Judul & Link
        // Struktur: <div class="item-article"> <header> <h2> <a href="...">Judul</a>
        val titleElement = element.selectFirst("div.item-article a") ?: return null
        
        val title = titleElement.text()
        val href = titleElement.attr("href")

        // 2. Ambil Poster
        // Struktur: <div class="content-thumbnail"> <a...> <img src="...">
        val posterElement = element.selectFirst("div.content-thumbnail img")
        val posterUrl = posterElement?.let { img ->
            // Cek data-src (lazy load) dulu, kalau kosong baru src
            img.attr("data-src").ifEmpty { img.attr("src") }
        }

        // 3. Cek Tipe (Series atau Movie)
        // Indikator: Ada teks "EPS" di overlay gambar atau judul mengandung "Season"
        val isSeries = element.text().contains("EPS", ignoreCase = true) || 
                       title.contains("Season", ignoreCase = true)

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

        // Mengambil Judul Utama
        val title = document.selectFirst("h1.entry-title")?.text() ?: "No Title"
        
        // Mengambil Poster Resolusi Tinggi di halaman detail
        val poster = document.selectFirst("div.gmr-poster img")?.let { img ->
             img.attr("data-src").ifEmpty { img.attr("src") }
        } ?: document.selectFirst("img.wp-post-image")?.attr("src")

        // Mengambil Deskripsi/Sinopsis
        val description = document.select("div.entry-content p").text()
        
        // Cek Episode (Khusus Series)
        // Tema GMR biasanya menaruh list episode di div.gmr-listseries
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
            }
        } else {
            // Jika tidak ada list episode, berarti Movie biasa
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
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

        // Mencari semua iframe video player
        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            // Filter: Abaikan link iklan Google/Facebook
            if (!src.contains("google") && !src.contains("facebook")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
