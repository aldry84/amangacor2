package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {

    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Headers standar (Wajib ada Referer)
    val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

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

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val doc = app.get(url, headers = mainHeaders).document
        return doc.select("article.item").mapNotNull { toSearchResult(it) }
    }

    // --- FUNGSI BANTUAN URL GAMBAR ---
    // Mengambil resolusi terbaik dari data-src atau srcset
    private fun getPosterUrl(element: Element): String? {
        val img = element.selectFirst("img.wp-post-image") ?: element.selectFirst("img")
        return img?.let {
            var url = it.attr("data-src")
            if (url.isEmpty()) {
                // Ambil url pertama dari srcset (biasanya resolusi beda dipisah koma)
                url = it.attr("srcset").split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
            }
            if (url.isEmpty()) {
                url = it.attr("src")
            }
            fixUrl(url)
        }
    }

    private fun fixUrl(url: String): String {
        if (url.isEmpty()) return ""
        var result = url.trim()
        if (result.startsWith("//")) result = "https:$result"
        if (result.startsWith("/")) result = "$mainUrl$result"
        return result
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.selectFirst("h2.entry-title a")?.text() ?: return null
        val href = element.selectFirst("h2.entry-title a")?.attr("href") ?: return null
        val posterUrl = getPosterUrl(element)
        val quality = element.selectFirst(".gmr-quality-item a")?.text()
        
        val isTv = element.select(".gmr-posttype-item").text().contains("TV Show", true) || 
                   element.select(".gmr-numbeps").isNotEmpty()

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                if (!quality.isNullOrBlank()) addQuality(quality)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mainHeaders).document

        val title = doc.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val description = doc.selectFirst(".entry-content p")?.text()
        
        // Poster: Cari di container detail gmr-movie-data
        val poster = getPosterUrl(doc.selectFirst("div.gmr-movie-data") ?: doc)

        // BACKDROP FIX:
        // Cek ID khusus backdrop, kalau gak ada (null/kosong), PAKSA pakai poster.
        // Ini mengatasi masalah "poster besar diatas tidak muncul".
        val backdropUrlRaw = doc.selectFirst("#muvipro_player_content_id img")?.attr("src")
        val backdrop = if (!backdropUrlRaw.isNullOrBlank()) fixUrl(backdropUrlRaw) else poster

        val year = doc.select("div.gmr-moviedata a[href*='year']").text().toIntOrNull()
        
        // Rating
        val ratingText = doc.select("span[itemprop=ratingValue]").text()
        val scoreVal = Score.from10(ratingText)

        // Metadata
        val tags = doc.select("div.gmr-moviedata a[href*='genre']").map { it.text() }
        val actors = doc.select("span[itemprop=actors] a").map { 
            ActorData(Actor(it.text(), null)) 
        }

        // Rekomendasi
        val recommendations = doc.select("div.idmuvi-core article.item").mapNotNull { 
            toSearchResult(it) 
        }

        val episodeList = doc.select(".gmr-listseries a")
        
        if (episodeList.isNotEmpty()) {
            val episodes = episodeList.mapNotNull { 
                val epHref = it.attr("href")
                val epText = it.text()
                
                if (it.hasClass("gmr-all-serie") || epText.contains("Pilih Episode", true)) {
                    return@mapNotNull null
                }

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
                this.score = scoreVal
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
                this.score = scoreVal
                this.tags = tags
                this.actors = actors
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mainHeaders).document

        // --- 1. Link Download (PRIORITAS TINGGI) ---
        // Biasanya berisi Google Drive atau FilePress (kualitas bagus)
        doc.select("ul.gmr-download-list li a").forEach { link ->
            val href = link.attr("href")
            val text = link.text() // misal: "Google Drive 1080p"
            
            if (href.startsWith("http")) {
                // Gunakan loadExtractor untuk otomatis handle gdrive/filepress/telegra
                loadExtractor(href, data, subtitleCallback, callback)
            }
        }

        // Fungsi Helper: Ekstrak link dari dalam iframe wrapper
        suspend fun extractWrapper(url: String) {
            val fixedUrl = fixUrl(url)
            
            // Cek apakah ini link wrapper (rpmlive/player)
            if (fixedUrl.contains("rpmlive") || fixedUrl.contains("playerngefilm")) {
                try {
                    // Request ke wrapper dengan Referer halaman film utama
                    val wrapperDoc = app.get(fixedUrl, headers = mapOf("Referer" to data)).document
                    
                    // 1. Cari Iframe di dalam wrapper
                    val innerIframe = wrapperDoc.select("iframe").attr("src")
                    if (innerIframe.isNotBlank()) {
                        loadExtractor(fixUrl(innerIframe), fixedUrl, subtitleCallback, callback)
                    } 
                    // 2. Cari Script yang mungkin mengandung link (untuk kasus tertentu)
                    else {
                        // Fallback: coba load URL wrapper itu sendiri, siapa tau didukung extractor
                        loadExtractor(fixedUrl, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) {
                    // Jika gagal akses wrapper, coba load langsung URL-nya
                    loadExtractor(fixedUrl, data, subtitleCallback, callback)
                }
            } else if (!fixedUrl.contains("youtube.com") && !fixedUrl.contains("youtu.be")) {
                // Link langsung (bukan wrapper)
                loadExtractor(fixedUrl, data, subtitleCallback, callback)
            }
        }

        // --- 2. Iframe Utama (Embed Responsive) ---
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) {
                extractWrapper(src)
            }
        }
        
        // --- 3. Tab Server Lain ---
        doc.select("ul.muvipro-player-tabs li a").forEach { tab ->
            val link = fixUrl(tab.attr("href"))
            
             if (link.isNotBlank() && link != data && !link.contains("#")) {
                 try {
                     val embedPage = app.get(link, headers = mainHeaders).document
                     val iframeSrc = embedPage.select("div.gmr-embed-responsive iframe").attr("src")
                     
                     if(iframeSrc.isNotBlank()) {
                         extractWrapper(iframeSrc)
                     }
                 } catch (e: Exception) {
                 }
             }
        }

        return true
    }
}
