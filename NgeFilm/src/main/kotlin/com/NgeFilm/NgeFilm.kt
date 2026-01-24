package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

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
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // Kategori sesuai website
    override val mainPage = mainPageOf(
        "$mainUrl/year/2026/" to "Film Terbaru 2026",
        "$mainUrl/populer/" to "Populer",
        "$mainUrl/Genre/action/" to "Action",
        "$mainUrl/Genre/horror/" to "Horror",
        "$mainUrl/country/indonesia/" to "Indonesia",
        "$mainUrl/country/korea/" to "Drama Korea"
    )

    // Engine Poster HD: Menghapus dimensi (misal -152x228) agar gambar jernih
    private fun String?.toHighDef(): String? {
        val url = this ?: return null
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        return fullUrl.substringBefore("?").replace(Regex("-\\d+x\\d+"), "")
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        val isTv = this.select(".gmr-numbeps").isNotEmpty() || href.contains("/tv/") || href.contains("/eps/")
        
        // FIX POSTER KOSONG: Cek data-src dan data-lazy-src untuk bypass Lazy Loading
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
        
        // Ambil poster HD dari OpenGraph meta tag
        val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")?.toHighDef()
        val description = document.select(".entry-content p").text().trim()
        val year = document.select(".gmr-moviedata:contains(Tahun) a").text().toIntOrNull()
        val trailer = document.selectFirst(".gmr-trailer-popup")?.attr("href")

        // FIX EPISODE: Pakai newEpisode agar tidak deprecated
        val episodes = document.select(".gmr-listseries a").filter { 
            !it.text().contains("Pilih Episode", true) && !it.hasClass("gmr-all-serie")
        }.map {
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
        
        // 1. Ambil ID Post untuk menembak AJAX Player
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")
        
        // 2. Loop AJAX dari tab p1 sampai p6 (Multi-Server)
        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            (1..6).forEach { i ->
                try {
                    val response = app.post(
                        ajaxUrl,
                        data = mapOf("action" to "muvipro_player_content", "tab" to "p$i", "post_id" to postId),
                        headers = mainHeaders + ("X-Requested-With" to "XMLHttpRequest")
                    ).text

                    // Regex untuk ambil link iframe di dalam respon AJAX
                    val iframeSrc = Regex("""<iframe.*?src=["'](.*?)["']""").find(response)?.groupValues?.get(1)
                    if (!iframeSrc.isNullOrBlank()) {
                        val finalSrc = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                        loadExtractor(finalSrc, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) { }
            }
        }

        // 3. Fallback: Ambil dari link download sebagai cadangan
        document.select(".gmr-download-list a").forEach { 
            loadExtractor(it.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
