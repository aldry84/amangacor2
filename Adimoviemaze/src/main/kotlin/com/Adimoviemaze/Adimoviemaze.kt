package com.Adimoviemaze

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.extractors.Gofile
import com.lagradost.cloudstream3.extractors.StreamTape
import kotlinx.coroutines.runBlocking

class Adimoviemaze : MainAPI() {
    // Pastikan mainUrl tetap hardcoded jika tidak ada domain API
    override var mainUrl: String = "https://moviemaze.cc"
    override var name                 = "Adimoviemaze"
    override val hasMainPage          = true
    override var lang                 = "en" 
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)
    
    // Header standar untuk menghindari blokir dari server
    private val standardHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
    )

    override val mainPage = mainPageOf(
        "trending" to "Trending", 
        "genre/action" to "Action",
        "genre/horror" to "Horror",
        "genre/comedy" to "Comedy",
        "genre/tv-series" to "TV Series",
        "movies" to "All Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val pagePath = if (request.data.contains("/")) request.data else "${request.data}/"
        val pageUrl = if (page == 1) {
            "$mainUrl/$pagePath"
        } else {
            // Asumsi Moviemaze menggunakan struktur /page/N/
            "$mainUrl/$pagePath/page/$page/"
        }

        val document = app.get(pageUrl, headers = standardHeaders).document
        
        // Selector yang lebih spesifik dan kokoh untuk DooTheme
        val home = document.select("div.items > article.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            // Asumsi halaman selanjutnya ada jika ada hasil
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Menggunakan selector yang lebih pasti
        val titleElement = this.selectFirst("div.data > h3 > a") ?: return null
        val title     = titleElement.text().trim()
        val href      = fixUrl(titleElement.attr("href"))
        
        // Ambil gambar dari attribute data-src atau src
        val posterUrl = fixUrlNull(this.select("div.poster img").attr("data-src").ifEmpty { 
            this.select("div.poster img").attr("src") 
        }) ?: ""

        if (title.isBlank() || href.isBlank()) return null
        
        // Cek tipe dari class/tag yang ada
        val tvType = if (this.selectFirst(".type.tv") != null) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl.ifEmpty { 
                // Poster default jika gagal
                "https://i.imgur.com/L7p41lQ.png" 
            } 
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        // Pencarian menggunakan parameter 's'
        val searchUrl = "$mainUrl/page/$page/?s=$query"
        val document = app.get(searchUrl, headers = standardHeaders).document
        
        val results = document.select("div.items > article.item").mapNotNull { it.toSearchResult() }
        
        return newSearchResponseList(results, results.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = standardHeaders).document
        
        // Ambil data dasar
        val title       = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster      = document.selectFirst("div.poster > img")?.attr("src")?.let { fixUrl(it) } ?: ""
        val description = document.selectFirst("div.wp-content > p")?.text()?.trim()
        
        // Ambil data tambahan (Rating, Genre, Tahun)
        val rating      = document.selectFirst("span.rating")?.text()?.toRatingInt()
        val genres      = document.select("div.sgeneros > a").map { it.text() }
        val year        = document.selectFirst("span.year")?.text()?.toIntOrNull()
        
        // Cek tipe (biasanya ada div.seasons untuk serial)
        val isSeries = document.selectFirst("div.seasons") != null
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        if (title.isBlank()) return null
        
        return if (isSeries) {
            // Logika untuk TV Series: Mengambil Season dan Episode
            val seasons = document.select("div.seasons > a").mapNotNull { seasonElement ->
                val seasonNum = seasonElement.attr("data-tab").toIntOrNull() ?: return@mapNotNull null
                val seasonName = seasonElement.text().trim()
                
                // Ambil episode berdasarkan season ID
                val episodes = document.select("div#season-$seasonNum ul.episodios > li").mapNotNull { episodeElement ->
                    val epTitleFull = episodeElement.selectFirst("a")?.text()?.trim() ?: ""
                    val epUrl       = fixUrl(episodeElement.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                    
                    // Coba ekstrak nomor episode (E1, E2, dst)
                    val epNum = Regex("E(\\d+)").find(epTitleFull)?.groupValues?.get(1)?.toIntOrNull()

                    newEpisode(epUrl) {
                        name    = epTitleFull
                        season  = seasonNum
                        episode = epNum
                    }
                }.toMutableList()
                
                // Tambahkan Episodes ke list Season
                episodes
            }.flatten().toMutableList()

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, seasons) {
                this.posterUrl = poster.ifEmpty { "https://i.imgur.com/L7p41lQ.png" }
                this.plot      = description
                this.year      = year
                this.rating    = rating
                this.tags      = genres
            }
        } else {
            // Logika untuk Movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster.ifEmpty { "https://i.imgur.com/L7p41lQ.png" }
                this.plot      = description
                this.year      = year
                this.rating    = rating
                this.tags      = genres
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        // data adalah URL film/episode
        try {
            val document = app.get(data, headers = standardHeaders).document
            
            // Loop melalui opsi player (Dooplay/DooTheme)
            document.select("#player-option-1 ul > li[data-post][data-nonce][data-nume]").amap { li ->
                val serverId = li.attr("data-post")
                val serverNonce = li.attr("data-nonce")
                val serverIdNum = li.attr("data-nume")
                val serverName  = li.selectFirst("span.title")?.text() ?: "Unknown Player"
                
                if (serverId.isNotBlank() && serverNonce.isNotBlank() && serverIdNum.isNotBlank()) {
                    // Endpoint AJAX untuk mengambil link player
                    val playerUrl = app.post(
                        url = "$mainUrl/wp-json/dooplay/v2/player_ad/", 
                        data = mapOf(
                            "post" to serverId,
                            "nonce" to serverNonce,
                            "nume" to serverIdNum
                        ),
                        referer = data,
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "User-Agent" to standardHeaders["User-Agent"]!!
                        )
                    ).document.selectFirst("iframe")?.attr("src")
                    
                    if (playerUrl != null && playerUrl.isNotBlank()) {
                        // Memuat link dari extractor eksternal
                        loadExtractor(playerUrl, data, subtitleCallback, callback)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false // Gagal memuat link
        }
        
        return true
    }
}
