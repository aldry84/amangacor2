package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {

    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm21"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Headers standar untuk browsing situs utama
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

    // --- FUNGSI BANTUAN GAMBAR (Fix Poster Hilang) ---
    private fun getPosterUrl(element: Element): String? {
        // Ambil elemen gambar, prioritas wp-post-image
        val img = element.selectFirst("img.wp-post-image") ?: element.selectFirst("img")
        return img?.let {
            // Cek data-src dulu (resolusi tinggi)
            var url = it.attr("data-src")
            if (url.isEmpty()) {
                // Cek srcset, ambil url paling belakang (biasanya resolusi terbesar)
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

    // --- LOAD DETAIL (Fix Backdrop & Info) ---
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = mainHeaders).document

        val title = doc.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val description = doc.selectFirst(".entry-content p")?.text()
        
        // Poster: Cari di container detail
        val poster = getPosterUrl(doc.selectFirst("div.gmr-movie-data") ?: doc)

        // BACKDROP FIX: Cek elemen ID khusus, kalau gak ada, PAKSA pakai poster.
        // Ini solusi untuk screenshot kamu yang gelap di atas.
        val backdropUrlRaw = doc.selectFirst("#muvipro_player_content_id img")?.attr("src")
        val backdrop = if (!backdropUrlRaw.isNullOrBlank()) fixUrl(backdropUrlRaw) else poster

        val year = doc.select("div.gmr-moviedata a[href*='year']").text().toIntOrNull()
        
        val ratingText = doc.select("span[itemprop=ratingValue]").text()
        val scoreVal = Score.from10(ratingText)

        val tags = doc.select("div.gmr-moviedata a[href*='genre']").map { it.text() }
        val actors = doc.select("span[itemprop=actors] a").map { 
            ActorData(Actor(it.text(), null)) 
        }

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

    // --- LOAD LINKS (PLAYER MAGIC: TXT & M3U8 Hunter) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, headers = mainHeaders).document

        // 1. Link Download (Prioritas Tinggi karena kualitas bagus)
        doc.select("ul.gmr-download-list li a").forEach { link ->
            val href = link.attr("href")
            val text = link.text() 
            if (href.startsWith("http")) {
                loadExtractor(href, data, subtitleCallback) { linkData ->
                    callback(linkData.copy(name = "NgeFilm - $text"))
                }
            }
        }

        // Fungsi Helper: Ekstrak player dari wrapper (RPMLIVE)
        suspend fun extractWrapper(url: String) {
            val fixedUrl = fixUrl(url)
            
            if (fixedUrl.contains("rpmlive") || fixedUrl.contains("playerngefilm")) {
                try {
                    // Ambil source HTML dari wrapper player
                    // PENTING: Gunakan referer halaman film utama
                    val response = app.get(fixedUrl, headers = mapOf("Referer" to data)).text
                    
                    // CARA BARU (TXT HUNTER): Cari link .txt atau .m3u8 di dalam script
                    // Regex ini mencari URL yang diapit tanda kutip dan mengandung .txt atau .m3u8
                    // Contoh temuan: "https://s3u.mindspireventures.sbs/.../cf-master...txt"
                    val regex = Regex("[\"'](https?://.*?(?:\\.txt|\\.m3u8).*?)[\"']")
                    val matches = regex.findAll(response)
                    
                    matches.forEach { match ->
                        val streamUrl = match.groupValues[1]
                        
                        // Header khusus agar tidak 403 Forbidden (Sesuai CURL kamu)
                        val streamHeaders = mapOf(
                            "Origin" to "https://playerngefilm21.rpmlive.online",
                            "Referer" to "https://playerngefilm21.rpmlive.online/",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        )

                        // Jika ketemu link .txt atau .m3u8, proses dengan M3u8Helper
                        if (streamUrl.contains(".txt") || streamUrl.contains(".m3u8")) {
                            M3u8Helper.generateM3u8(
                                "NgeFilm-VIP",
                                streamUrl,
                                "https://playerngefilm21.rpmlive.online/", // Referer wajib ini
                                headers = streamHeaders
                            ).forEach { link ->
                                callback(link)
                            }
                        }
                    }
                    
                    // Fallback: Cari Iframe lain di dalamnya
                    val wrapperDoc = org.jsoup.Jsoup.parse(response)
                    val innerIframe = wrapperDoc.select("iframe").attr("src")
                    if (innerIframe.isNotBlank()) {
                        loadExtractor(fixUrl(innerIframe), fixedUrl, subtitleCallback, callback)
                    }

                } catch (e: Exception) {
                    // Jika gagal, coba load langsung (siapa tau support)
                    loadExtractor(fixedUrl, data, subtitleCallback, callback)
                }
            } else if (!fixedUrl.contains("youtube.com") && !fixedUrl.contains("youtu.be")) {
                loadExtractor(fixedUrl, data, subtitleCallback, callback)
            }
        }

        // 2. Scan Iframe Utama
        doc.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotBlank()) extractWrapper(src)
        }
        
        // 3. Scan Tab Server
        doc.select("ul.muvipro-player-tabs li a").forEach { tab ->
            val link = fixUrl(tab.attr("href"))
             if (link.isNotBlank() && link != data && !link.contains("#")) {
                 try {
                     val embedPage = app.get(link, headers = mainHeaders).document
                     val iframeSrc = embedPage.select("div.gmr-embed-responsive iframe").attr("src")
                     if(iframeSrc.isNotBlank()) extractWrapper(iframeSrc)
                 } catch (e: Exception) {
                 }
             }
        }

        return true
    }
}
