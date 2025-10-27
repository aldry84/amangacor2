package com.Adimoviemaze

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.runBlocking

class Adimoviemaze : MainAPI() {
    override var mainUrl: String = "https://moviemaze.cc"
    override var name                 = "Adimoviemaze"
    // ... (property lainnya tidak berubah)
    override val hasMainPage          = true
    override var lang                 = "en" 
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)
    
    private val standardHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
    )
    
    private val defaultPoster = "https://i.imgur.com/L7p41lQ.png"

    override val mainPage = mainPageOf(
        "trending" to "Trending", 
        "genre/action" to "Action",
        "genre/horror" to "Horror",
        "genre/tv-series" to "TV Series",
        "movies" to "All Movies"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val pagePath = if (request.data.contains("/")) request.data else "${request.data}/"
        val pageUrl = "$mainUrl/$pagePath/page/$page/"

        val document = app.get(pageUrl, headers = standardHeaders).document
        
        // REVISI SELECTOR UTAMA: 
        // Mencoba selector yang lebih generik untuk item di halaman index DooTheme.
        // Jika div.items > article.item gagal, kita coba div.items.
        val home = document.select("div.items article.item, div.items > article").mapNotNull { it.toSearchResult() }
        
        // Jika halaman Kategori adalah halaman index biasa, terkadang mereka menggunakan .result-item
        if (home.isEmpty()) {
             // Selector alternatif untuk hasil pencarian/kategori jika struktur berbeda
             return document.select("div.result-item, article.item").mapNotNull { it.toSearchResult() }
        }

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
        
        // REVISI SELECTOR POSTER & JUDUL:
        // Mencari elemen <a> terdekat yang memiliki href (link ke film)
        val titleElement = this.selectFirst("div.data a, h3 a") ?: this.selectFirst("a") ?: return null
        val href      = fixUrl(titleElement.attr("href"))
        
        // Judul diambil dari teks <a>
        val title     = titleElement.text().trim().ifEmpty { 
             this.selectFirst("h3")?.text()?.trim() ?: "" 
        }

        // POSTER: Mencoba beberapa lokasi umum untuk gambar (img di div.poster atau langsung di article)
        val posterUrl = fixUrlNull(
            this.select("div.poster img").attr("data-src").ifEmpty { // data-src (lazy load)
                this.select("div.poster img").attr("src").ifEmpty { // src
                    this.select("img").attr("src") // img langsung
                } 
            }
        )
        
        if (title.isBlank() || href.isBlank()) return null
        
        // Tipe TV: Cek apakah ada badge 'type tv'
        val tvType = if (this.selectFirst(".type.tv") != null) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, href, tvType) {
            this.posterUrl = posterUrl ?: defaultPoster
        }
    }
    
    // ... (fungsi search, load, dan loadLinks tidak perlu diubah, karena masalah utama ada di list halaman utama)
    
    // ...
}
