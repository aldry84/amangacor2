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
    // Mengambil kotak film dari halaman utama/pencarian
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
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/anime/page/" to "Anime"
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
        
        // Mengambil kualitas (HD/CAM) jika ada
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
        
        // Parsing Tahun & Rating
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()
        val rating = document.selectFirst(".gmr-meta-rating .gmr-rating-value")?.text()?.toRatingInt()

        // Deteksi apakah ini Series atau Movie
        val type = if (url.contains("/tv/") || title.contains("Episode")) TvType.TvSeries else TvType.Movie

        return newMovieLoadResponse(title, url, type, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.rating = rating
        }
    }

    // ==========================================
    // 5. PEMUTAR VIDEO (LoadLinks - FINAL FIX)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // 1. Ambil Post ID (Wajib untuk AJAX)
        // ID ini ada di div #muvipro_player_content_id
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        // 2. Loop semua tab server (p1, p2, p3...)
        document.select("ul#gmr-tab li a").forEach { tab ->
            val tabHref = tab.attr("href") // Contoh: #p1
            val tabId = tabHref.removePrefix("#") // Contoh: p1
            
            // Cek apakah iframe sudah ada langsung (Server 1 biasanya langsung load)
            val directIframe = document.selectFirst("div$tabHref iframe")
            
            if (directIframe != null) {
                // KASUS A: Video langsung tersedia
                val src = directIframe.attr("src")
                loadExtractor(fixUrl(src), callback, subtitleCallback)
            } 
            else if (postId != null) {
                // KASUS B: Video perlu dipanggil via AJAX (Server 2, 3, dst)
                try {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    
                    // Header Referer ditambahkan sesuai data curl kamu agar tidak ditolak
                    val headers = mapOf(
                        "Referer" to data,
                        "X-Requested-With" to "XMLHttpRequest"
                    )

                    val formBody = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tabId,
                        "post_id" to postId
                    )

                    // Kirim Request
                    val response = app.post(ajaxUrl, headers = headers, data = formBody).text
                    
                    // Cari link video (src="...") di dalam respons AJAX
                    val iframeMatch = Regex("src=\"(.*?)\"").find(response)
                    if (iframeMatch != null) {
                        // Bersihkan URL (kadang ada escape character backslash)
                        val src = iframeMatch.groupValues[1].replace("\\", "")
                        loadExtractor(fixUrl(src), callback, subtitleCallback)
                    }
                } catch (e: Exception) {
                    // Lanjut ke server berikutnya jika error
                }
            }
        }
        return true
    }

    // Helper untuk memperbaiki URL (misal: //embedpyrox.xyz -> https://embedpyrox.xyz)
    private fun fixUrl(url: String): String {
        return if (url.startsWith("//")) "https:$url" else url
    }
}
