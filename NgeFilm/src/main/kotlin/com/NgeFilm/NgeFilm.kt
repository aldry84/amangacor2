package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class NgeFilm : ParsersHttpProvider() {

    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Headers untuk menghindari blokir (menyamar sebagai Chrome)
    override val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    // =========================================================================
    // 1. HALAMAN UTAMA (Home)
    // =========================================================================
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Terbaru",
        "$mainUrl/populer/page/" to "Populer",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/horror/page/" to "Horror",
        "$mainUrl/country/indonesia/page/" to "Indonesia"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val doc = app.get(url, headers = mainHeaders).document
        val home = doc.select("article.item").mapNotNull { toSearchResult(it) }
        return newHomePageResponse(request.name, home)
    }

    // =========================================================================
    // 2. PENCARIAN (Search)
    // =========================================================================
    override suspend fun search(query: String): List<SearchResponse> {
        // Filter agar mencari Movie (post) dan Series (tv)
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val doc = app.get(url, headers = mainHeaders).document
        return doc.select("article.item").mapNotNull { toSearchResult(it) }
    }

    // Fungsi konversi Element HTML -> SearchResponse Cloudstream
    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = element.selectFirst("h2.entry-title a")?.attr("href") ?: return null
        
        // Ambil poster, prioritaskan src, fallback ke data-src (lazy load)
        val posterUrl = element.selectFirst("img")?.let { img ->
            img.attr("src").ifEmpty { img.attr("data-src") }
        }

        val quality = element.selectFirst(".gmr-quality-item a")?.text()
        
        // Deteksi Series vs Movie berdasarkan indikator di HTML
        val isTv = element.select(".gmr-posttype-item").text().contains("TV Show", true) || 
                   element.select(".gmr-numbeps").isNotEmpty()

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    // =========================================================================
    // 3. DETAIL (Load Info)
    // =========================================================================
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mainHeaders).document

        val title = doc.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val description = doc.selectFirst(".entry-content p")?.text()
        
        val poster = doc.selectFirst("div.gmr-movie-data img")?.attr("src") 
            ?: doc.selectFirst("div.post-thumbnail img")?.attr("src")
            
        val year = doc.select("div.gmr-moviedata a[href*='year']").text().toIntOrNull()
        val rating = doc.select("span[itemprop=ratingValue]").text().toDoubleOrNull()?.times(1000)?.toInt()
        val backdrop = doc.selectFirst("#muvipro_player_content_id img")?.attr("src") ?: poster

        // Ambil Genre (Tags) dan Aktor
        val tags = doc.select("div.gmr-moviedata a[href*='genre']").map { it.text() }
        val actors = doc.select("span[itemprop=actors] a").map { it.text() }

        // Ambil Rekomendasi (Film Terkait)
        val recommendations = doc.select("div.idmuvi-core .row.grid-container article.item").mapNotNull { 
            toSearchResult(it) 
        }

        // Cek apakah ada list episode
        val episodeList = doc.select(".gmr-listseries a")
        
        if (episodeList.isNotEmpty()) {
            val episodes = episodeList.mapNotNull { 
                val epHref = it.attr("href")
                val epText = it.text()
                
                // Filter tombol "Pilih Episode" agar tidak dianggap episode
                if (it.hasClass("gmr-all-serie") || epText.contains("Pilih Episode", true)) {
                    return@mapNotNull null
                }

                // Ubah "Eps1" jadi "Episode 1"
                val cleanName = if(epText.startsWith("Eps", true)) {
                    epText.replace("Eps", "Episode ")
                } else {
                    epText
                }

                newEpisode(epHref) {
                    this.name = cleanName
                    this.episode = cleanName.filter { char -> char.isDigit() }.toIntOrNull()
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = description
                this.year = year
                this.rating = rating
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.plot = description
                this.year = year
                this.rating = rating
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
    }

    // =========================================================================
    // 4. LOAD LINKS (Video Sources)
    // =========================================================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mainHeaders).document

        // [A] Cek Link Download (Biasanya kualitas bagus: GDrive, FilePress)
        doc.select("ul.gmr-download-list li a").forEach { link ->
            val href = link.attr("href")
            if (href.startsWith("http")) {
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }

        // [B] Cek Iframe Player Utama (Di atas tombol lampu)
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var sourceUrl = iframe.attr("src")
            if (sourceUrl.startsWith("//")) sourceUrl = "https:$sourceUrl"
            
            // Filter: Jangan load trailer Youtube
            if (!sourceUrl.contains("youtube.com") && !sourceUrl.contains("youtu.be")) {
                loadExtractor(sourceUrl, data, subtitleCallback, callback)
            }
        }
        
        // [C] Cek Tab Server Lain (Server 2, Server 3, dst)
        doc.select("ul.muvipro-player-tabs li a").forEach { tab ->
            var link = tab.attr("href")
            
            // Fix URL jika formatnya relative "/judul/?player=2"
            if (link.startsWith("/")) {
                link = mainUrl + link
            }
            
            // Proses link valid, hindari loop ke halaman sendiri
             if (link.startsWith("http") && link != data && !link.contains("#")) {
                 try {
                     val embedPage = app.get(link, headers = mainHeaders).document
                     val iframeSrc = embedPage.select("div.gmr-embed-responsive iframe").attr("src")
                     
                     if(iframeSrc.isNotBlank()) {
                         var realSrc = iframeSrc
                         if (realSrc.startsWith("//")) realSrc = "https:$realSrc"
                         
                         if (!realSrc.contains("youtube.com") && !realSrc.contains("youtu.be")) {
                             loadExtractor(realSrc, data, subtitleCallback, callback)
                         }
                     }
                 } catch (e: Exception) {
                     // Lanjut ke tab berikutnya jika error
                 }
             }
        }

        return true
    }
}
