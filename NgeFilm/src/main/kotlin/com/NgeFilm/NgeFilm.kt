package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.jsoup.Jsoup

class NgeFilmProvider : MainAPI() {
    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NGEFILM21"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val mainHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/",
        "X-Requested-With" to "XMLHttpRequest"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/year/2026/" to "Film Terbaru 2026",
        "$mainUrl/populer/" to "Populer",
        "$mainUrl/Genre/action/" to "Action",
        "$mainUrl/Genre/horror/" to "Horror",
        "$mainUrl/country/indonesia/" to "Indonesia",
        "$mainUrl/country/korea/" to "Drama Korea"
    )

    // --- ENGINE GAMBAR ANTI-BLUR & BYPASS LAZY LOAD ---
    private fun String?.toHighDef(): String? {
        val url = this ?: return null
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        // Hapus penanda ukuran (misal -152x228) agar gambar jernih HD
        return fullUrl.substringBefore("?").replace(Regex("-\\d+x\\d+"), "")
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        val isTv = this.select(".gmr-numbeps").isNotEmpty() || href.contains("/tv/") || href.contains("/eps/")
        
        // Ambil atribut poster yang bener (data-src atau src)
        val img = this.selectFirst("img")
        val rawPoster = img?.attr("data-src").takeIf { !it.isNullOrBlank() } 
                     ?: img?.attr("data-lazy-src").takeIf { !it.isNullOrBlank() }
                     ?: img?.attr("src")
        
        val posterUrl = rawPoster.toHighDef()
        val quality = this.selectFirst(".gmr-quality-item")?.text() ?: "HD"

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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}page/$page/" else request.data
        val doc = app.get(url, headers = mainHeaders).document
        val home = doc.select("article").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val doc = app.get(url, headers = mainHeaders).document
        return doc.select("article").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mainHeaders).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        
        // Poster HD dari meta tag og:image agar pasti muncul
        val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")?.toHighDef()
        val description = document.select(".entry-content p").text().trim()
        val year = document.select(".gmr-moviedata:contains(Tahun) a").text().toIntOrNull()
        
        // Trailer yang bener-bener akurat dari popup
        val trailer = document.selectFirst(".gmr-trailer-popup")?.attr("href")

        val episodes = document.select(".gmr-listseries a").filter { 
            !it.text().contains("Pilih Episode", true) && !it.hasClass("gmr-all-serie")
        }.map {
            // Gunakan newEpisode sesuai standar terbaru agar tidak error build
            newEpisode(it.attr("href")) {
                this.name = it.text().trim()
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addTrailer(trailer) // FIX TRAILER
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addTrailer(trailer) // FIX TRAILER
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = mainHeaders).document
        
        // 1. Ambil ID Post buat AJAX player
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")
        
        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            // Loop tab p1 sampe p6 buat dapet link streaming sakti
            (1..6).forEach { i ->
                try {
                    val response = app.post(
                        ajaxUrl,
                        data = mapOf("action" to "muvipro_player_content", "tab" to "p$i", "post_id" to postId),
                        headers = mainHeaders
                    ).text

                    // REGEX HUNTER: Ambil link iframe atau m3u8 dari respon AJAX
                    val iframeSrc = Regex("""<iframe.*?src=["'](.*?)["']""").find(response)?.groupValues?.get(1)
                    val directM3u8 = Regex("""["'](https?://.*?\.m3u8.*?)["']""").find(response)?.groupValues?.get(1)

                    if (!directM3u8.isNullOrBlank()) {
                        M3u8Helper.generateM3u8("NgeFilm VIP $i", directM3u8, "$mainUrl/").forEach { link -> callback(link) }
                    } else if (!iframeSrc.isNullOrBlank()) {
                        val finalSrc = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                        loadExtractor(finalSrc, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) { }
            }
        }

        // 2. Link Download Box sebagai cadangan
        document.select(".gmr-download-list a").forEach { 
            loadExtractor(it.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
