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
    // 1. SELECTOR UTAMA
    // ==========================================
    // Sesuai gambar: konten ada di div.gmr-maincontent -> row
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
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")

        val posterElement = element.selectFirst(".content-thumbnail img")
        val posterUrl = posterElement?.attr("src")
        val quality = element.selectFirst(".gmr-quality-item a")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
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

        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
        val plot = document.selectFirst(".entry-content p")?.text()
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()
        val rating = document.selectFirst(".gmr-meta-rating .gmr-rating-value")?.text()?.toRatingInt()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.rating = rating
        }
    }

    // ==========================================
    // 5. PEMUTAR VIDEO (LoadLinks - FINAL)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // 1. Ambil Post ID (96555) dari elemen yang kamu temukan di gambar
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        // 2. Cari semua tab server (Server 1, Server 2...) di dalam ul#gmr-tab
        document.select("ul#gmr-tab li a").forEach { tab ->
            val tabHref = tab.attr("href") // Contoh: #p1, #p2
            val tabId = tabHref.removePrefix("#") // Jadi: p1, p2
            
            // Cek apakah di halaman itu videonya sudah langsung ada (seperti Server 1 di gambar)
            val directIframe = document.selectFirst("div$tabHref iframe")
            
            if (directIframe != null) {
                // KASUS A: Video langsung ketemu (Server 1)
                val src = directIframe.attr("src")
                loadExtractor(src, callback, subtitleCallback)
            } 
            else if (postId != null) {
                // KASUS B: Video masih kosong (Server 2, 3..), panggil AJAX
                try {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    
                    // Kita kirim formulir rahasia ke server mereka
                    val formBody = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tabId,
                        "post_id" to postId
                    )
                    
                    // Tembak servernya!
                    val responseHtml = app.post(ajaxUrl, data = formBody).text
                    
                    // Cari iframe di jawaban server
                    // Regex ini mencari src="..." di dalam teks jawaban
                    val iframeMatch = Regex("src=\"(.*?)\"").find(responseHtml)
                    if (iframeMatch != null) {
                        val src = iframeMatch.groupValues[1]
                        loadExtractor(src, callback, subtitleCallback)
                    }
                } catch (e: Exception) {
                    // Jika satu server gagal, lanjut ke server berikutnya saja
                }
            }
        }
        return true
    }
}
