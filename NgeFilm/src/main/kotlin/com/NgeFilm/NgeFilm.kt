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
        "$mainUrl/country/indonesia/" to "Indonesia",
        "$mainUrl/country/korea/" to "Drama Korea"
    )

    private fun String?.toHighDef(): String? {
        val url = this ?: return null
        return url.substringBefore("?").replace(Regex("-\\d+x\\d+"), "")
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        
        val img = this.selectFirst("img")
        val rawPoster = img?.attr("data-src").takeIf { !it.isNullOrBlank() } 
                     ?: img?.attr("data-lazy-src").takeIf { !it.isNullOrBlank() }
                     ?: img?.attr("src")
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = rawPoster.toHighDef()
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
        val poster = document.selectFirst("meta[property=\"og:image\"]")?.attr("content")?.toHighDef()
        val plot = document.select(".entry-content p").text().trim()
        
        val trailer = document.selectFirst(".gmr-trailer-popup")?.attr("href")
                   ?: document.selectFirst("a[href*='youtube.com/watch']")?.attr("href")

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
                this.plot = plot
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                addTrailer(trailer)
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
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")
        
        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            // Loop semua tab server (p1 sampai p6) sesuai temuan log
            (1..6).forEach { i ->
                try {
                    val response = app.post(
                        ajaxUrl,
                        data = mapOf("action" to "muvipro_player_content", "tab" to "p$i", "post_id" to postId),
                        headers = mainHeaders
                    ).text

                    val iframeSrc = Regex("""<iframe.*?src=["'](.*?)["']""").find(response)?.groupValues?.get(1)
                    if (!iframeSrc.isNullOrBlank()) {
                        val finalSrc = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                        loadExtractor(finalSrc, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) { }
            }
        }

        // Cek link download sebagai cadangan tautan pemutaran
        document.select(".gmr-download-list a").forEach { 
            loadExtractor(it.attr("href"), data, subtitleCallback, callback)
        }

        return true
    }
}
