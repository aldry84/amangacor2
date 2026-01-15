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

    // 1. PENGATURAN KATEGORI (Sesuai Gambar 14 & 15)
    override val mainPage = mainPageOf(
        "$mainUrl/category/movie/page/" to "Bioskop Terbaru",
        "$mainUrl/category/serial-tv/page/" to "Serial TV",
        "$mainUrl/category/drama-asia/page/" to "Drama Asia",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/horror/page/" to "Horror"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        
        // Selektor kartu film berdasarkan class MuviPro
        val home = document.select("article.item-infinite").mapNotNull {
            val titleEl = it.selectFirst(".entry-title a") ?: return@mapNotNull null
            val poster = it.selectFirst(".content-thumbnail img")?.attr("src")
            
            newMovieSearchResponse(titleEl.text(), titleEl.attr("href"), TvType.Movie) {
                this.posterUrl = poster
            }
        }
        return newHomePageResponse(request.name, home)
    }

    // 2. LOGIKA PENCARIAN
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url).document
        return document.select("article.item-infinite").mapNotNull {
            val titleEl = it.selectFirst(".entry-title a") ?: return@mapNotNull null
            newMovieSearchResponse(titleEl.text(), titleEl.attr("href"), TvType.Movie) {
                this.posterUrl = it.selectFirst(".content-thumbnail img")?.attr("src")
            }
        }
    }

    // 3. LOGIKA SERIES & DETAIL (Perbaikan Berdasarkan Gambar 12)
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
        
        // MENCARI DAFTAR EPISODE (Selector pasti: .gmr-listseries a)
        val episodeElements = document.select(".gmr-listseries a")
        
        return if (episodeElements.isNotEmpty()) {
            val episodes = episodeElements.mapIndexed { index, element ->
                newEpisode(element.attr("href")) {
                    this.name = element.text()
                    this.episode = index + 1
                }
            }.reversed() // Episode 1 di atas
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
            }
        } else {
            // Jika bukan series atau halaman episode tunggal
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
            }
        }
    }

    // 4. PENGAMBILAN LINK (Sesuai Gambar 13 & Data CURL)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Ambil Post ID dari class body 'postid-XXXX' (Pasti berfungsi, Lihat Gambar 13)
        val bodyClass = document.selectFirst("body")?.className() ?: ""
        val postId = Regex("""postid-(\d+)""").find(bodyClass)?.groupValues?.get(1)
                   ?: document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        if (postId == null) return false

        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        
        // Menguji Server p1 sampai p3 (Berdasarkan Gambar 6)
        for (i in 1..3) {
            try {
                val response = app.post(
                    ajaxUrl,
                    headers = mapOf(
                        "Referer" to data,
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                    ),
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to "p$i",
                        "post_id" to postId
                    )
                ).text
                
                // Regex presisi untuk link iframe
                val iframeSrc = Regex("""src=["'](.*?)["']""").find(response)?.groupValues?.get(1)
                if (iframeSrc != null) {
                    val cleanUrl = iframeSrc.replace("\\", "")
                    val finalUrl = if (cleanUrl.startsWith("//")) "https:$cleanUrl" else cleanUrl
                    loadExtractor(finalUrl, data, subtitleCallback, callback)
                }
            } catch (e: Exception) { }
        }
        return true
    }
}
