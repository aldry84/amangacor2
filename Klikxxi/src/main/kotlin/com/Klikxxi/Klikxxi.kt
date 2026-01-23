package com.Klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Klikxxi : MainAPI() {
    override var mainUrl = "https://klikxxi.me"
    override var name = "KlikXXI"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --- DEFINISI KATEGORI ---
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Home",
        "$mainUrl/category/action/" to "Eksen",
        "$mainUrl/category/adventure/" to "Petualangan",
        "$mainUrl/category/crime/" to "Kriminal",
        "$mainUrl/category/drama/" to "Drama",
        "$mainUrl/category/horror/" to "Horror",
        "$mainUrl/category/mystery/" to "Misteri",
        "$mainUrl/category/science-fiction/" to "Science & Fiction",
        "$mainUrl/category/war/" to "War"
    )

    // --- HELPER UNTUK GAMBAR HD ---
    private fun String?.toLargeUrl(): String? {
        val url = this ?: return null
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        return fullUrl.replace(Regex("-\\d+x\\d+"), "")
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")

        // DETEKSI TIPE KONTEN (Movie vs TV)
        val isTv = href.contains("/tv/")

        val imgElement = this.selectFirst("img")
        val rawPoster = imgElement?.attr("data-lazy-src") ?: imgElement?.attr("src")
        val posterUrl = rawPoster.toLargeUrl()

        val quality = this.selectFirst(".gmr-quality-item")?.text() ?: "N/A"
        
        // Ambil Rating (Jika ada di website)
        val ratingText = this.selectFirst(".gmr-rating-item")?.text()?.trim()
        val rating = ratingText?.toDoubleOrNull()

        if (isTv) {
            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = getQualityFromString(quality)
                if (rating != null) {
                    this.score = Score.from10(rating)
                }
            }
        } else {
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = getQualityFromString(quality)
                if (rating != null) {
                    this.score = Score.from10(rating)
                }
            }
        }
    }

    // --- CUSTOM EXTRACTOR UNTUK STRP2P ---
    private suspend fun invokeStrp2p(
        url: String,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val id = url.substringAfter("/embed/").substringBefore("?")
            val domain = "https://klikxxi.strp2p.site"

            // Langkah 1: Request Token
            val apiVideoUrl = "$domain/api/v1/video?id=$id&w=360&h=800&r=klikxxi.me"
            
            val tokenResponse = app.get(
                apiVideoUrl,
                headers = mapOf(
                    "Referer" to url,
                    "Origin" to domain,
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                )
            ).text

            if (tokenResponse.contains("success\":false")) return

            // Langkah 2: Request Player dengan Token
            val apiPlayerUrl = "$domain/api/v1/player?t=$tokenResponse"
            
            val playerResponse = app.get(
                apiPlayerUrl,
                headers = mapOf(
                    "Referer" to url,
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36"
                )
            ).parsedSafe<Strp2pResponse>()

            // Langkah 3: Ambil link m3u8
            playerResponse?.source?.forEach { source ->
                callback.invoke(
                    newExtractorLink(
                        "StrP2P (VIP)",
                        "StrP2P ${source.label}",
                        source.file,
                        mainUrl,
                        Qualities.Unknown.value
                    ) {
                        this.isM3u8 = true
                    }
                )
            }
        } catch (e: Exception) {
            // Ignore error
        }
    }

    data class Strp2pResponse(
        val source: List<Strp2pSource>? = null
    )

    data class Strp2pSource(
        val file: String,
        val label: String
    )

    // --- MAIN FUNCTIONS ---

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val homeSets = ArrayList<HomePageList>()
        
        // Handle Pagination untuk Kategori
        val url = if (page > 1) {
            if (request.data.endsWith("/")) "${request.data}page/$page/" else "${request.data}/page/$page/"
        } else {
            request.data
        }

        val document = app.get(url).document

        if (request.name == "Home") {
            // Logic khusus Halaman Utama (Widget)
            document.select(".muvipro-posts-module").forEach { widget ->
                val title = widget.select(".homemodule-title").text().trim()
                val movies = widget.select(".gmr-item-modulepost").mapNotNull { it.toSearchResponse() }
                if (movies.isNotEmpty()) {
                    homeSets.add(HomePageList(title, movies))
                }
            }
        } else {
            // Logic untuk Kategori (Eksen, Petualangan, dll)
            val movies = document.select("article.item").mapNotNull { it.toSearchResponse() }
            if (movies.isNotEmpty()) {
                homeSets.add(HomePageList(request.name, movies))
            }
        }

        return newHomePageResponse(homeSets)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type%5B%5D=post&post_type%5B%5D=tv"
        val document = app.get(url).document
        return document.select("article.item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        
        val imgTag = document.selectFirst("img.attachment-thumbnail") 
                     ?: document.selectFirst(".gmr-poster img")
                     ?: document.selectFirst("figure img")
        val rawPoster = imgTag?.attr("data-lazy-src") ?: imgTag?.attr("src")
        val poster = rawPoster.toLargeUrl()
        val description = document.select(".entry-content p").text().trim()
        val year = document.select("time[itemprop=dateCreated]").text().takeLast(4).toIntOrNull()
        
        // Coba ambil rating dari detail page jika ada
        val ratingMeta = document.selectFirst("meta[itemprop=ratingValue]")?.attr("content") 
                        ?: document.selectFirst(".gmr-rating-item")?.text()?.trim()
        val scoreVal = ratingMeta?.toDoubleOrNull()

        // --- DETEKSI TV SERIES & EPISODE ---
        val episodes = ArrayList<Episode>()
        val episodeElements = document.select(".gmr-season-episodes a")
        
        if (episodeElements.isNotEmpty()) {
             episodeElements.forEach { eps ->
                 val epsUrl = eps.attr("href")
                 val epsName = eps.text().trim() 
                 
                 if (!epsName.contains("Batch", ignoreCase = true)) {
                     val regex = Regex("S(\\d+)Eps(\\d+)", RegexOption.IGNORE_CASE)
                     val match = regex.find(epsName)
                     
                     val seasonNum = match?.groupValues?.get(1)?.toIntOrNull()
                     val episodeNum = match?.groupValues?.get(2)?.toIntOrNull()
                     
                     episodes.add(
                         newEpisode(epsUrl) {
                             this.name = epsName
                             this.season = seasonNum
                             this.episode = episodeNum
                         }
                     )
                 }
             }
             
             return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                if (scoreVal != null) this.score = Score.from10(scoreVal)
            }
        } 
        
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            if (scoreVal != null) this.score = Score.from10(scoreVal)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            (1..6).forEach { index ->
                try {
                    val response = app.post(
                        ajaxUrl,
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to "p$index",
                            "post_id" to postId
                        ),
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                    ).text

                    val ajaxDoc = org.jsoup.Jsoup.parse(response)
                    ajaxDoc.select("iframe").forEach { iframe ->
                        var src = iframe.attr("src")
                        if (src.startsWith("//")) src = "https:$src"
                        
                        if (src.contains("strp2p") || src.contains("auvexiug")) {
                             invokeStrp2p(src, callback)
                        } else if (!src.contains("facebook") && !src.contains("whatsapp")) {
                            loadExtractor(src, data, subtitleCallback, callback)
                        }
                    }
                } catch (e: Exception) { }
            }
        }

        document.select(".gmr-download-list a").forEach { link ->
            loadExtractor(link.attr("href"), data, subtitleCallback, callback)
        }
        
        document.select("iframe").forEach { iframe ->
             var src = iframe.attr("src")
             if (src.startsWith("//")) src = "https:$src"
             if (!src.contains("youtube") && !src.contains("facebook")) {
                 loadExtractor(src, data, subtitleCallback, callback)
             }
        }

        return true
    }
}
