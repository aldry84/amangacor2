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

    // 1. Selector Utama
    override val searchSelector = "div.gmr-maincontent .row article.item-infinite"

    // 2. Halaman Utama
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

    // 3. Parsing Kartu Film
    override fun searchFromElement(element: Element): SearchResponse? {
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = element.selectFirst(".content-thumbnail img")?.attr("src")
        val quality = element.selectFirst(".gmr-quality-item a")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            if (!quality.isNullOrEmpty()) addQuality(quality)
        }
    }

    // 4. Halaman Detail
    override fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
        val plot = document.selectFirst(".entry-content p")?.text()
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()
        val rating = document.selectFirst(".gmr-meta-rating .gmr-rating-value")?.text()?.toRatingInt()
        
        // Cek tipe (Movie atau TV)
        val type = if (url.contains("/tv/") || title.contains("Episode")) TvType.TvSeries else TvType.Movie

        return newMovieLoadResponse(title, url, type, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
            this.rating = rating
        }
    }

    // ==========================================
    // 5. PEMUTAR VIDEO (DISEMPURNAKAN DARI DATA CURL)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        document.select("ul#gmr-tab li a").forEach { tab ->
            val tabHref = tab.attr("href") // #p1, #p2
            val tabId = tabHref.removePrefix("#") // p1, p2
            
            // Helper function untuk memproses link yang ditemukan
            suspend fun resolveLink(url: String) {
                var fixUrl = url
                if (fixUrl.startsWith("//")) fixUrl = "https:$fixUrl"
                
                // EmbedPyrox dan server lain akan otomatis dideteksi di sini
                loadExtractor(fixUrl, data, subtitleCallback, callback)
            }

            // Cek 1: Apakah iframe sudah ada langsung? (Biasanya Server 1)
            val directIframe = document.selectFirst("div$tabHref iframe")
            if (directIframe != null) {
                resolveLink(directIframe.attr("src"))
            } 
            // Cek 2: Jika tidak ada, panggil via AJAX (Sesuai Data CURL kamu)
            else if (postId != null) {
                try {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    
                    // Header Referer PENTING agar server tidak menolak (Sesuai CURL)
                    val headers = mapOf(
                        "Referer" to data,
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                    )

                    val formBody = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to tabId,
                        "post_id" to postId
                    )

                    val response = app.post(ajaxUrl, headers = headers, data = formBody).text
                    
                    // Regex untuk mencari src iframe di dalam response text
                    val iframeMatch = Regex("src=\"(.*?)\"").find(response)
                    if (iframeMatch != null) {
                        // Bersihkan URL dari karakter backslash jika ada (contoh: https:\/\/...)
                        val cleanUrl = iframeMatch.groupValues[1].replace("\\", "")
                        resolveLink(cleanUrl)
                    }
                } catch (e: Exception) {
                    // Skip jika server ini gagal
                }
            }
        }
        return true
    }
}
