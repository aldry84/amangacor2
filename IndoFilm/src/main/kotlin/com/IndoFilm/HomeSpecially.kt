package com.IndoFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HomeSpecially : MainAPI() {
    override var mainUrl = "https://homespecially.com"
    override var name = "HomeSpecially"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Kita hapus 'override' karena di MainAPI variabel ini tidak ada secara default
    val searchSelector = "div.gmr-maincontent .row article.item-infinite"

    // ==========================================
    // 1. HALAMAN UTAMA (Main Page)
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        
        // Memanggil searchSelector yang kita buat di atas
        val home = document.select(searchSelector).mapNotNull {
            toSearchResult(it)
        }
        
        return newHomePageResponse(request.name, home)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
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

    // ==========================================
    // 2. PENCARIAN (Search)
    // ==========================================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url).document

        return document.select(searchSelector).mapNotNull {
            toSearchResult(it)
        }
    }

    // ==========================================
    // 3. HALAMAN DETAIL (Load)
    // ==========================================
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
        val plot = document.selectFirst(".entry-content p")?.text()
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()
        
        val type = if (url.contains("/tv/") || title.contains("Episode")) TvType.TvSeries else TvType.Movie

        return newMovieLoadResponse(title, url, type, url) {
            this.posterUrl = poster
            this.plot = plot
            this.year = year
        }
    }

    // ==========================================
    // 4. PEMUTAR VIDEO (LoadLinks)
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
            val tabHref = tab.attr("href") 
            val tabId = tabHref.removePrefix("#") 
            
            suspend fun resolveLink(url: String) {
                var fixUrl = url
                if (fixUrl.startsWith("//")) fixUrl = "https:$fixUrl"
                loadExtractor(fixUrl, data, subtitleCallback, callback)
            }

            val directIframe = document.selectFirst("div$tabHref iframe")
            if (directIframe != null) {
                resolveLink(directIframe.attr("src"))
            } 
            else if (postId != null) {
                try {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    
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
                    
                    val iframeMatch = Regex("src=\"(.*?)\"").find(response)
                    if (iframeMatch != null) {
                        val cleanUrl = iframeMatch.groupValues[1].replace("\\", "")
                        resolveLink(cleanUrl)
                    }
                } catch (e: Exception) {
                }
            }
        }
        return true
    }
}
