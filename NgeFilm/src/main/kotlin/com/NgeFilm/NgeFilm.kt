package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {
    // URL utama sering berubah (new31, new19, dll), jadi kita pakai variabel agar mudah diganti
    override var mainUrl = "https://new31.ngefilm.site" 
    override var name = "NgeFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Header standar agar tidak dianggap bot
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    )

    // Kategori diambil valid dari HTML <select name="genre">
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Terbaru", // Halaman depan (pagination)
        "$mainUrl/Genre/action/page/" to "Action",
        "$mainUrl/Genre/horror/page/" to "Horror",
        "$mainUrl/Genre/drama/page/" to "Drama",
        "$mainUrl/Genre/comedy/page/" to "Comedy",
        "$mainUrl/Genre/thriller/page/" to "Thriller",
        "$mainUrl/Genre/romance/page/" to "Romance",
        "$mainUrl/Genre/animation/page/" to "Animation",
        "$mainUrl/country/indonesia/page/" to "Indonesia",
        "$mainUrl/country/korea/page/" to "Drama Korea"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Format URL: https://site.com/Genre/action/page/2/
        val url = if (page == 1) {
            request.data.removeSuffix("page/") // Halaman 1 tanpa /page/
        } else {
            "${request.data}$page/"
        }

        val document = app.get(url, headers = commonHeaders).document
        
        // Target: <article class="item-infinite ...">
        val home = document.select("article.item-infinite").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        
        // Ambil gambar: Coba data-src dulu (lazy load), kalau kosong baru src
        val imgTag = this.selectFirst("img.attachment-medium")
        val posterUrl = imgTag?.attr("data-src")?.ifEmpty { imgTag.attr("src") } 
            ?: imgTag?.attr("src")

        // Ambil info tambahan dari HTML
        val quality = this.select("div.gmr-quality-item a").text() // WEB-DL, Bluray
        val rating = this.select("div.gmr-rating-item").text().trim() // 6.8
        val duration = this.select("div.gmr-duration-item").text().trim() // 148 min
        
        // Cek apakah ini TV Series (biasanya ada total episode atau kategori TV)
        val isSeries = href.contains("/tv/") || this.select(".gmr-numbeps").isNotEmpty()

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
                addDuration(duration)
                // Rating di Cloudstream butuh Int 0-100 atau 0-10 (double)
                // Kita masukkan ke footer text saja biar aman tampilnya
                this.addText(rating) 
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Format search dari form HTML: /?s=avatar&post_type[]=post&post_type[]=tv
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url, headers = commonHeaders).document
        
        return document.select("article.item-infinite").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, headers = commonHeaders).document
        
        val title = document.selectFirst("h1.entry-title")?.text() ?: return null
        val poster = document.selectFirst("div.gmr-poster img")?.attr("src")
        val plot = document.selectFirst("div.entry-content p")?.text()
        
        // Parsing tahun dari meta data
        val yearText = document.select("span.year").text() // Jika ada class year
        val year = Regex("\\d{4}").find(yearText ?: document.text())?.value?.toIntOrNull()

        val isSeries = url.contains("/tv/") || document.select("div.gmr-listseries").isNotEmpty()

        if (isSeries) {
            val episodes = document.select("div.gmr-listseries a").map {
                val epsName = it.text()
                // Ekstrak nomor episode, misal "Episode 8" -> 8
                val epsNum = Regex("Ep\\.?\\s*(\\d+)").find(epsName)?.groupValues?.get(1)?.toIntOrNull()
                
                newEpisode(it.attr("href")) {
                    this.name = epsName
                    this.episode = epsNum
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        val document = app.get(data, headers = commonHeaders).document
        
        // Loop semua iframe yang ada
        document.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var link = iframe.attr("src")
            
            // LOGIKA UNSHORTEN & ROUTING SERVER
            // 1. Cek Unshorten (short.icu / hglink)
            if (link.contains("short.icu") || link.contains("hglink.to")) {
                link = app.get(link, headers = commonHeaders).url
            }

            // 2. Cek Server RpmLive (Custom Extractor)
            if (link.contains("rpmlive.online")) {
                RpmLive().getStreamUrl(link, callback)
            } else {
                // 3. Sisanya serahkan ke Extractor bawaan (Kraken, Hxfile, dll)
                loadExtractor(link, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
