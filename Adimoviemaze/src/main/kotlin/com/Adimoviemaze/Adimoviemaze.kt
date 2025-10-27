package com.Adimoviemaze

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking

class Adimoviemaze : MainAPI() {
    override var mainUrl: String = "https://moviemaze.cc" // URL utama yang Anda berikan
    override var name                 = "Adimoviemaze"
    override val hasMainPage          = true
    override var lang                 = "en" // Asumsi bahasa Inggris/multinasional
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)

    // Kategori dari halaman utama moviemaze.cc (berdasarkan inspeksi umum situs sejenis)
    override val mainPage = mainPageOf(
        "trending" to "Trending", // Asumsi endpoint trending
        "genre/action" to "Action",
        "genre/horror" to "Horror",
        "genre/comedy" to "Comedy",
        "genre/tv-series" to "TV Series",
        "movies" to "All Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val pageUrl = if (page == 1) {
            // Untuk halaman 1, kita asumsikan struktur URL tanpa nomor halaman atau dengan /page/1
            if (request.data.contains("/"))
                "$mainUrl/${request.data}/page/1/"
            else
                "$mainUrl/${request.data}"
        } else {
            // Untuk halaman berikutnya
            if (request.data.contains("/"))
                "$mainUrl/${request.data}/page/$page/"
            else
                "$mainUrl/${request.data}/page/$page/" // Asumsi endpoint utama juga berhalaman
        }

        val document = app.get(pageUrl).document
        
        // Selector umum untuk item film/serial di halaman utama/kategori
        val home = document.select("div.items > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty() // Cek sederhana apakah ada hasil
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // Asumsi struktur DOM umum untuk thumbnail
        val titleElement = this.selectFirst("div.data > h3 > a") ?: return null
        val title     = titleElement.text().trim()
        val href      = fixUrl(titleElement.attr("href"))
        val posterUrl = fixUrlNull(this.select("div.poster > img").attr("src"))

        if (title.isBlank() || href.isBlank() || posterUrl.isNullOrBlank()) return null

        // Cek tipe (Movie atau TvSeries)
        val tvType = if (this.selectFirst(".type.tv") != null) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        // Moviemaze umumnya menggunakan parameter 's' untuk pencarian dan penomoran halaman
        val searchUrl = "$mainUrl/page/$page/?s=$query"
        val document = app.get(searchUrl).document
        
        val results = document.select("div.items > article").mapNotNull { it.toSearchResult() }
        
        return newSearchResponseList(results, results.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        // Ambil data dasar
        val title       = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val poster      = document.selectFirst("div.poster > img")?.attr("src")?.let { fixUrl(it) } ?: ""
        val description = document.selectFirst("div.wp-content > p")?.text()?.trim()
        
        // Cek tipe (biasanya ada tag/class untuk serial)
        val isSeries = document.selectFirst(".seasons") != null
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie
        
        // Ambil tahun (jika ada)
        val yearString = document.selectFirst("span.year")?.text()?.trim()
        val year = yearString?.toIntOrNull()

        if (title.isBlank()) return null
        
        return if (isSeries) {
            // Logika untuk TV Series
            val episodes = document.select(".se-c ul li").mapNotNull { element ->
                val episodeTitle = element.selectFirst(".episodio a")?.text()?.trim() ?: "Unknown Episode"
                val episodeUrl   = fixUrl(element.selectFirst(".episodio a")?.attr("href") ?: return@mapNotNull null)
                
                newEpisode(episodeUrl) {
                    name = episodeTitle
                }
            }
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes.reversed().toMutableList()) {
                this.posterUrl = poster
                this.plot      = description
                this.year      = year
            }
        } else {
            // Logika untuk Movie
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot      = description
                this.year      = year
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        
        // Cari iframe atau link player. Asumsi ada section 'Player'
        document.select("#playeroptions ul > li").amap { li ->
            val serverId = li.attr("data-post")
            val serverNonce = li.attr("data-nonce")
            val serverIdNum = li.attr("data-nume")
            
            if (serverId.isNotBlank() && serverNonce.isNotBlank() && serverIdNum.isNotBlank()) {
                val playerUrl = app.post(
                    url = "$mainUrl/wp-json/dooplay/v2/player_ad/", // Endpoint umum Dooplay/DooTheme
                    data = mapOf(
                        "post" to serverId,
                        "nonce" to serverNonce,
                        "nume" to serverIdNum
                    ),
                    referer = data,
                    headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest"
                    )
                ).document.selectFirst("iframe")?.attr("src")
                
                if (playerUrl != null && playerUrl.isNotBlank()) {
                    loadExtractor(playerUrl, data, subtitleCallback, callback)
                }
            }
        }
        
        return true
    }
}
