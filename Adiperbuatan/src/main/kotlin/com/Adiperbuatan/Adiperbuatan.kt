package com.adiperbuatan

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.GdrivePlayer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.newMovieSearchResponse

class Adiperbuatan : MainAPI() {
    override var mainUrl = "https://prmovies.com"
    override var name = "Adiperbuatan"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie) 

    override val mainPage: List<MainPageData>
        get() = listOf(
            MainPageData("Latest Movies", ""),
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // PERBAIKAN URL: Menggunakan pola yang lebih kuat untuk halaman utama
        val url = if (page == 1) "$mainUrl/movies/" else "$mainUrl/movies/page/$page/"
        val doc = app.get(url).document

        val items = doc.select("div.TPost.M > article").mapNotNull { element ->
            val title = element.selectFirst("h2.Title")?.text() ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            // Mengambil poster dari atribut 'data-src' karena lazy loading
            val posterUrl = element.selectFirst("img.lazy")?.attr("data-src")

            newMovieSearchResponse(
                name = title,
                url = href,
                type = TvType.Movie
            ) {
                this.posterUrl = posterUrl
            }
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
            ),
            hasNext = true 
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document

        // Selektor yang sama dengan halaman utama, karena prmovies.com sering menggunakan tata letak yang konsisten
        return doc.select("div.TPost.M > article").mapNotNull { element ->
            val title = element.selectFirst("h2.Title")?.text() ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = element.selectFirst("img.lazy")?.attr("data-src")

            newMovieSearchResponse(
                name = title,
                url = href,
                type = TvType.Movie
            ) {
                this.posterUrl = posterUrl
            }
        }
    }

    // FUNGSI PARSING UTAMA: MENGAMBIL DETAIL DAN URL PLAYER
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        
        val title = doc.selectFirst("h1.Title")?.text() ?: return null
        val poster = doc.selectFirst("div.Image img.lazy")?.attr("data-src")
        
        // Asumsi plot berada di dalam div deskripsi yang luas
        val descript = doc.selectFirst("div.TPost.Bg p")?.text()
        
        // Logika PARSING URL PLAYER: Mencari iframe yang berisi player atau link GDrive.
        // Ini adalah 'dataUrl' yang akan diteruskan ke loadLinks.
        val playerEmbedUrl = doc.selectFirst("iframe[src*=\"player\"]")?.attr("src") 
            ?: doc.selectFirst("iframe[src*=\"drive.google.com\"]")?.attr("src")
            ?: doc.selectFirst("iframe[src]")?.attr("src") // Coba ambil iframe pertama jika yang lain gagal

        if (playerEmbedUrl == null) {
            // Jika tidak ada iframe, film ini mungkin tidak dapat dimainkan.
            return null
        }

        return newMovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            dataUrl = playerEmbedUrl // Meneruskan URL player/embed sebagai dataUrl
        ) {
            this.posterUrl = poster
            this.plot = descript
        }
    }

    // FUNGSI PENARIK TAUTAN (Extractor)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val embedUrl = data // 'data' adalah URL player/embed dari fungsi load()

        // 1. Menggunakan Extractor CloudStream bawaan (untuk GDrive, StreamSB, dll.)
        // Ini adalah cara paling efisien dan stabil.
        if (loadExtractor(embedUrl, mainUrl, subtitleCallback, callback)) {
            return true
        }

        // 2. Fallback GDrive manual (jika loadExtractor tidak berfungsi)
        if (embedUrl.contains("drive.google.com")) {
            GdrivePlayer().load(embedUrl, mainUrl, subtitleCallback, callback)
            return true
        }
        
        // Catatan: Extractor kustom PrMoviesCustomExtractor harus diimplementasikan
        // jika link streaming tidak dapat ditarik oleh loadExtractor standar.
        
        return false // Gagal jika tidak ada tautan yang dapat ditarik
    }
}
