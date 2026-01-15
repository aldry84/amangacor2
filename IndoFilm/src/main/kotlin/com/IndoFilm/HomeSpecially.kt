package com.IndoFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.Jsoup

class HomeSpecially : ParsedHttpSource() {
    override val name = "HomeSpecially"
    override var mainUrl = "https://homespecially.com"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ==========================================
    // 1. SELECTOR UTAMA
    // ==========================================
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
        
        // Ambil Post ID untuk keperluan AJAX (contoh: 96555 dari gambar kamu)
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        // Cari semua tab server (Server 1, Server 2, dst)
        // Selektor berdasarkan id="gmr-tab" di gambar 6
        document.select("ul#gmr-tab li a").forEach { tab ->
            val tabId = tab.attr("href").removePrefix("#") // Hasil: p1, p2, p3...
            val serverName = tab.text() // Hasil: "Server 1", "Server 2"

            // Logika 1: Cek apakah videonya sudah ada langsung (Biasanya Server 1)
            val directIframe = document.selectFirst("div#$tabId iframe")
            if (directIframe != null) {
                val src = directIframe.attr("src")
                // LoadExtractor otomatis mengenali embedpyrox, streamtape, dll
                loadExtractor(src, callback, subtitleCallback)
            } 
            // Logika 2: Jika kosong (Server 2, 3..), kita panggil lewat AJAX
            else if (postId != null) {
                try {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    // Mengirim formulir rahasia ke server mereka
                    val formBody = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tabId,
                        "post_id" to postId
                    )
                    
                    // Request ke server
                    val responseHtml = app.post(ajaxUrl, data = formBody).text
                    
                    // Cari link iframe di dalam jawaban server
                    val iframeMatch = Regex("src=\"(.*?)\"").find(responseHtml)
                    if (iframeMatch != null) {
                        val src = iframeMatch.groupValues[1]
                        loadExtractor(src, callback, subtitleCallback)
                    }
                } catch (e: Exception) {
                    // Jika gagal load salah satu server, abaikan dan lanjut ke server berikutnya
                }
            }
        }
        return true
    }
}
