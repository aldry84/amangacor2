package com.Adimoviemaze

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking

class Adimoviemaze : MainAPI() {
    override var mainUrl: String = "https://moviemaze.cc"
    override var name                 = "Adimoviemaze"
    override val hasMainPage          = true
    override var lang                 = "en" 
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)
    
    // Header standar untuk mencegah blokir dan meniru browser
    private val standardHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
    )
    
    // Poster default jika tidak ada gambar ditemukan (ganti dengan URL default Anda)
    private val defaultPoster = "https://i.imgur.com/L7p41lQ.png"

    override val mainPage = mainPageOf(
        "trending" to "Trending", 
        "genre/action" to "Action",
        "genre/horror" to "Horror",
        "genre/tv-series" to "TV Series",
        "movies" to "All Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        // Struktur URL untuk DooTheme: /kategori/page/N/
        val pagePath = if (request.data.contains("/")) request.data else "${request.data}/"
        val pageUrl = "$mainUrl/$pagePath/page/$page/"

        val document = app.get(pageUrl, headers = standardHeaders).document
        
        // Selector umum untuk item pada DooTheme
        val home = document.select("div.items > article.item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.data > h3 > a") ?: return null
        val title     = titleElement.text().trim()
        val href      = fixUrl(titleElement.attr("href"))
        
        // Ambil poster dari data-src atau src (handle lazy loading)
        val posterUrl = fixUrlNull(this.select("div.poster img").attr("data-src").ifEmpty { 
            this.select("div.poster img").attr("src") 
        })
        
        if (title.isBlank() || href.isBlank()) return null
        
        val tvType = if (this.selectFirst(".type.tv") != null) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl ?: defaultPoster
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        // Pencarian: /page/N/?s=query
        val searchUrl = "$mainUrl/page/$page/?s=$query"
        val document = app.get(searchUrl, headers = standardHeaders).document
        
        val results = document.select("div.items > article.item").mapNotNull { it.toSearchResult() }
        
        return newSearchResponseList(results, results.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = standardHeaders).document
        
        // Data Dasar
        val title       = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster      = document.selectFirst("div.poster > img")?.attr("src")?.let { fixUrl(it) }
        val description = document.selectFirst("div.wp-content > p")?.text()?.trim()
        
        // Data Tambahan
        val rating      = document.selectFirst("span.rating")?.text()?.toRatingInt()
        val genres      = document.select("div.sgeneros a").map { it.text() }
        val year        = document.selectFirst("span.year")?.text()?.toIntOrNull()
        
        val isSeries = document.selectFirst("div.seasons") != null
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        if (title.isBlank()) return null
        
        return if (isSeries) {
            // Logika untuk TV Series: Mengambil Episode per Season
            val episodes = mutableListOf<Episode>()
            
            // Loop melalui Season tabs dan ambil episodenya
            document.select("div.seasons > a[data-tab]").forEach { seasonTab ->
                val seasonNum = seasonTab.attr("data-tab").toIntOrNull()
                if (seasonNum != null) {
                    // Selector untuk episode di season tertentu
                    document.select("div#season-$seasonNum ul.episodios > li").mapNotNull { episodeElement ->
                        val epUrl = fixUrl(episodeElement.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                        val epTitleFull = episodeElement.selectFirst("a")?.text()?.trim() ?: "Episode Unknown"
                        
                        // Coba ekstrak nomor episode (contoh: E01, E2)
                        val epNum = Regex("E(\\d+)(?: -|$)").find(epTitleFull)?.groupValues?.get(1)?.toIntOrNull()

                        episodes.add(
                            newEpisode(epUrl) {
                                name    = epTitleFull
                                season  = seasonNum
                                episode = epNum
                            }
                        )
                    }
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed().toMutableList()) {
                this.posterUrl = poster ?: defaultPoster
                this.plot      = description
                this.year      = year
                this.rating    = rating
                this.tags      = genres
            }
        } else {
            // Logika untuk Movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster ?: defaultPoster
                this.plot      = description
                this.year      = year
                this.rating    = rating
                this.tags      = genres
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        
        // 1. Ambil token player
        val document = app.get(data, headers = standardHeaders).document
        
        // 2. Loop melalui opsi player (DooTheme AJAX)
        document.select("#player-option-1 ul > li[data-post][data-nonce][data-nume]").amap { li ->
            val serverId = li.attr("data-post")
            val serverNonce = li.attr("data-nonce")
            val serverIdNum = li.attr("data-nume")
            
            if (serverId.isNotBlank() && serverNonce.isNotBlank() && serverIdNum.isNotBlank()) {
                
                // Lakukan POST request untuk mendapatkan iframe URL
                val postResponse = app.post(
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
                ).document
                
                // Ambil iframe src
                val playerUrl = postResponse.selectFirst("iframe")?.attr("src")
                
                if (playerUrl != null && playerUrl.isNotBlank()) {
                    // Gunakan loadExtractor generik dan extractor kustom
                    loadExtractor(playerUrl, data, subtitleCallback, callback)
                }
            }
        }
        
        return true
    }
}
