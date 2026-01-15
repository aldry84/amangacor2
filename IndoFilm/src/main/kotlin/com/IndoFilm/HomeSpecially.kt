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

    // ==============================
    // 1. PENGATURAN HALAMAN UTAMA
    // ==============================
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Terbaru",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/komedi/page/" to "Komedi",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/genre/drama/page/" to "Drama"
    )

    override fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        // MuviPro biasanya membungkus film dalam <article> atau class item-infinite
        return newHomePageResponse(request.name, url)
    }

    // ==============================
    // 2. SELEKTOR ELEMENT (Pencarian & Home)
    // ==============================
    override fun searchFromElement(element: Element): SearchResponse? {
        // Menganalisa struktur MuviPro standar
        
        // 1. Ambil Judul dan Link
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")

        // 2. Ambil Poster
        // Kadang src, kadang data-src (lazy load)
        val posterElement = element.selectFirst(".content-thumbnail img")
        val posterUrl = posterElement?.attr("data-src") ?: posterElement?.attr("src")

        // 3. Ambil Label Kualitas (Opsional, untuk info tambahan)
        val quality = element.selectFirst(".gmr-quality-item a")?.text() ?: ""

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            // Menambahkan kualitas ke judul (opsional, biar kelihatan di UI)
            // this.name = "$title [$quality]" 
        }
    }

    // ==============================
    // 3. HALAMAN DETAIL (Perlu dicek langkah berikutnya)
    // ==============================
    override fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst(".entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src") 
                    ?: document.selectFirst(".content-thumbnail img")?.attr("src")
        
        val plot = document.selectFirst(".entry-content p")?.text()
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
        }
    }

    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Nanti kita isi bagian ini setelah melihat halaman player
        return true
    }
}
