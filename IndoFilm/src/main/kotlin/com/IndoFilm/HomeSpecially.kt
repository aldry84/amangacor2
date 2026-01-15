package com.IndoFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HomeSpecially : ParsedHttpSource() {
    override val name = "HomeSpecially"
    override var mainUrl = "https://homespecially.com"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ==========================================
    // 1. SELECTOR UTAMA (PENTING)
    // ==========================================
    // Berdasarkan gambar 2, konten ada di dalam gmr-maincontent -> row.
    // Tema MuviPro biasanya membungkus setiap film dengan tag <article> yang punya class 'item-infinite'
    override val searchSelector = "div.gmr-maincontent .row article.item-infinite"

    // ==========================================
    // 2. HALAMAN UTAMA (Main Page)
    // ==========================================
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Terbaru",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/komedi/page/" to "Komedi",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/romance/page/" to "Romance",
        "$mainUrl/genre/drama/page/" to "Drama"
    )

    override fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        return newHomePageResponse(request.name, url)
    }

    // ==========================================
    // 3. PARSING KARTU FILM
    // ==========================================
    override fun searchFromElement(element: Element): SearchResponse? {
        // Struktur Standar MuviPro
        
        // Judul & Link biasanya ada di: <h2 class="entry-title"><a href="...">Judul</a></h2>
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")

        // Poster biasanya di: <div class="content-thumbnail"><img src="..."></div>
        val posterElement = element.selectFirst(".content-thumbnail img")
        val posterUrl = posterElement?.attr("src")

        // Label Kualitas (HD/CAM) - Opsional
        val quality = element.selectFirst(".gmr-quality-item a")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            // Menambahkan kualitas di belakang judul agar user tahu (misal: "Avengers [HD]")
            if (!quality.isNullOrEmpty()) {
                addQuality(quality) 
            }
        }
    }

    // ==========================================
    // 4. HALAMAN DETAIL (Load)
    // ==========================================
    override fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // MuviPro Detail Structure
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
        val plot = document.selectFirst(".entry-content p")?.text()
        
        // Mengambil tahun dari metadata
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()
        
        // Mengambil Rating
        val rating = document.selectFirst(".gmr-meta-rating .gmr-rating-value")?.text()?.toRatingInt()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.rating = rating
        }
    }

    // ==========================================
    // 5. PEMUTAR VIDEO (LoadLinks)
    // ==========================================
    // Bagian ini BELUM selesai karena kita belum lihat halaman playernya.
    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Placeholder: Mencoba mencari iframe standar MuviPro
        document.select("ul.muvipro-player-tabs li").forEach { tab ->
            // Logika untuk mengambil ID player biasanya ada di sini
            // Kita butuh analisis gambar selanjutnya untuk ini
        }
        
        return true
    }
}
