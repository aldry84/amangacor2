package com.IndoFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class HomeSpecially : MainAPI() {
    override var mainUrl = "https://homespecially.com"
    override var name = "HomeSpecially"
    override var lang = "id"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)

    val searchSelector = "div.gmr-maincontent .row article.item-infinite"

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
        val document = app.get(url, timeout = 30).document
        val home = document.select(searchSelector).mapNotNull { toSearchResult(it) }
        return newHomePageResponse(request.name, home)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = element.selectFirst(".content-thumbnail img")?.attr("src")
        val quality = element.selectFirst(".gmr-quality-item a")?.text()

        val type = if (href.contains("/tv/") || href.contains("/eps/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            if (!quality.isNullOrEmpty()) addQuality(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url, timeout = 30).document
        return document.select(searchSelector).mapNotNull { toSearchResult(it) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, timeout = 30).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
        val plot = document.selectFirst(".entry-content p")?.text()
        val year = document.selectFirst(".gmr-movie-data:contains(Tahun) a")?.text()?.toIntOrNull()

        // MENCARI DAFTAR EPISODE ASLI (Kunci perbaikan tampilan Seri)
        val episodeElements = document.select(".gmr-listseries a")
        
        return if (url.contains("/tv/") || url.contains("/eps/") || episodeElements.isNotEmpty()) {
            val episodes = if (episodeElements.isNotEmpty()) {
                episodeElements.mapIndexed { index, element ->
                    newEpisode(element.attr("href")) {
                        this.name = element.text()
                        this.episode = index + 1
                    }
                }.reversed()
            } else {
                listOf(
                    newEpisode(url) {
                        this.name = "Play Movie / Episode"
                        this.episode = 1
                    }
                )
            }
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, timeout = 30).document
        
        // Ambil ID dari body class atau container (Sangat krusial untuk AJAX)
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id") 
                   ?: document.selectFirst(".muvipro_player_content")?.attr("data-id")
                   ?: Regex("""postid-(\d+)""").find(document.selectFirst("body")?.className() ?: "")?.groupValues?.get(1)

        document.select("ul#gmr-tab li a").forEach { tab ->
            val tabHref = tab.attr("href") 
            val tabId = tabHref.removePrefix("#") 
            
            val directIframe = document.selectFirst("div$tabHref iframe")
            if (directIframe != null) {
                loadExtractor(fixUrl(directIframe.attr("src")), data, subtitleCallback, callback)
            } else if (postId != null) {
                try {
                    val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
                    val response = app.post(
                        ajaxUrl,
                        headers = mapOf(
                            "Referer" to data, 
                            "X-Requested-With" to "XMLHttpRequest",
                            // User Agent Mobile agar server mau memberikan tautan
                            "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                        ),
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to tabId,
                            "post_id" to postId
                        ),
                        timeout = 20
                    ).text
                    
                    val iframeSrc = Regex("""src=["'](.*?)["']""").find(response)?.groupValues?.get(1)
                    if (iframeSrc != null) {
                        loadExtractor(fixUrl(iframeSrc), data, subtitleCallback, callback)
                    }
                } catch (e: Exception) { }
            }
        }
        return true
    }

    private fun fixUrl(url: String): String {
        // Membersihkan karakter backslash dan memastikan https
        return if (url.startsWith("//")) "https:$url" else url.replace("\\", "")
    }
}
