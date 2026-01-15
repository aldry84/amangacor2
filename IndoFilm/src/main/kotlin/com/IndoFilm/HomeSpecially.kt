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

    // 1. DAFTAR KATEGORI UTAMA (Sesuai Gambar 14 & 15)
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
        // Selektor kartu film berdasarkan struktur MuviPro
        val home = document.select("article.item-infinite").mapNotNull { toSearchResult(it) }
        return newHomePageResponse(request.name, home)
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleEl = element.selectFirst(".entry-title a") ?: return null
        val href = titleEl.attr("href")
        // Deteksi tipe secara otomatis berdasarkan pola URL (tv/eps/movie)
        val type = if (href.contains("/tv/") || href.contains("/eps/")) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(titleEl.text(), href, type) {
            this.posterUrl = element.selectFirst(".content-thumbnail img")?.attr("src")
        }
    }

    // 2. LOGIKA DETAIL & EPISODE (Memperbaiki Tampilan Seri yang Kosong)
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        
        // Selektor Daftar Episode Asli (Berdasarkan Gambar 12: class .gmr-listseries)
        val episodeElements = document.select(".gmr-listseries a")
        
        return if (episodeElements.isNotEmpty()) {
            // Jika ditemukan list episode (Halaman /tv/)
            val episodes = episodeElements.mapIndexed { index, element ->
                newEpisode(element.attr("href")) {
                    this.name = element.text()
                    this.episode = index + 1
                }
            }.reversed() // Membalik urutan agar Episode 1 di atas
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
            }
        } else if (url.contains("/eps/")) {
            // Jika membuka halaman episode tunggal secara langsung
            val episodes = listOf(
                newEpisode(url) {
                    this.name = title
                    this.episode = 1
                }
            )
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
            }
        } else {
            // Jika konten adalah Film (Movie)
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = document.selectFirst(".gmr-movie-view-poster img")?.attr("src")
            }
        }
    }

    // 3. PENGAMBILAN LINK VIDEO (Menggunakan Post ID & AJAX Pasti)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Mengambil Post ID dari class body (Sesuai Gambar 13: postid-XXXX)
        val bodyClass = document.selectFirst("body")?.className() ?: ""
        val postId = Regex("""postid-(\d+)""").find(bodyClass)?.groupValues?.get(1)
                   ?: document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        if (postId == null) return false

        val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
        
        // Loop Server p1 - p3 (Berdasarkan tab pada Gambar 6 dan 11)
        for (i in 1..3) {
            try {
                val response = app.post(
                    ajaxUrl,
                    headers = mapOf(
                        "Referer" to data,
                        "X-Requested-With" to "XMLHttpRequest",
                        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36"
                    ),
                    data = mapOf(
                        "action" to "muvipro_player_content",
                        "tab" to "p$i",
                        "post_id" to postId
                    )
                ).text
                
                // Mencari URL Iframe (Misal: embedpyrox.xyz) di respons AJAX
                val iframeSrc = Regex("""src=["'](.*?)["']""").find(response)?.groupValues?.get(1)
                if (iframeSrc != null) {
                    val cleanUrl = iframeSrc.replace("\\", "")
                    val finalUrl = if (cleanUrl.startsWith("//")) "https:$cleanUrl" else cleanUrl
                    
                    // CloudStream akan otomatis menggunakan extractor untuk link embedpyrox
                    loadExtractor(finalUrl, data, subtitleCallback, callback)
                }
            } catch (e: Exception) { /* skip server bermasalah */ }
        }
        return true
    }
}
