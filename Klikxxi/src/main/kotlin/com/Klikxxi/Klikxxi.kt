package com.Klikxxi // DISAMAKAN DENGAN PLUGIN

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer // FIX UNRESOLVED TRAILER
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class KlikxxiProvider : MainAPI() { // Ganti nama class agar deskriptif
    override var mainUrl = "https://new31.ngefilm.site" // URL Klikxxi kamu
    override var name = "Klikxxi"
    override val hasMainPage = true
    override var lang = "id"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

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

    // --- ENGINE GAMBAR ANTI-BLUR ---
    private fun String?.toLargeUrl(): String? {
        val url = this ?: return null
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        return fullUrl.substringBefore("?").replace(Regex("-\\d+x\\d+"), "")
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        val isTv = this.select(".gmr-numbeps").isNotEmpty() || href.contains("/tv/") || href.contains("/eps/")
        val imgElement = this.selectFirst("img")
        val rawPoster = imgElement?.attr("data-src") ?: imgElement?.attr("src") ?: imgElement?.attr("data-lazy-src")
        val posterUrl = rawPoster.toLargeUrl()
        val quality = this.selectFirst(".gmr-quality-item")?.text() ?: "N/A"
        val ratingText = this.selectFirst(".gmr-rating-item")?.text()?.trim()
        val rating = ratingText?.toDoubleOrNull()

        return if (isTv) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addQuality(quality)
                if (rating != null) this.score = Score.from10(rating)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
                if (rating != null) this.score = Score.from10(rating)
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}$page/" else request.data
        val doc = app.get(url, headers = mainHeaders).document
        val home = doc.select("article.item").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type%5B%5D=post&post_type%5B%5D=tv"
        val doc = app.get(url, headers = mainHeaders).document
        return doc.select("article.item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = mainHeaders).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        val imgTag = document.selectFirst(".gmr-movie-data img") ?: document.selectFirst(".content-thumbnail img")
        val poster = (imgTag?.attr("data-src") ?: imgTag?.attr("src")).toLargeUrl()
        val backdropUrlRaw = document.selectFirst("#muvipro_player_content_id img")?.attr("src")
        val backdrop = if (!backdropUrlRaw.isNullOrBlank()) backdropUrlRaw.toLargeUrl() else poster
        val description = document.select(".entry-content p").text().trim()
        val year = document.select("time[itemprop=dateCreated]").text().takeLast(4).toIntOrNull()
        val ratingText = document.selectFirst("span[itemprop=ratingValue]")?.text()?.trim()
        val scoreVal = ratingText?.toDoubleOrNull()
        val trailer = document.selectFirst(".gmr-trailer-popup")?.attr("href")

        val episodes = ArrayList<Episode>()
        document.select(".gmr-listseries a").forEach { eps ->
            val epsUrl = eps.attr("href")
            val epsName = eps.text().trim() 
            if (!epsName.contains("Pilih Episode", true) && !eps.hasClass("gmr-all-serie")) {
                // FIX ERROR EPISODE: Pakai newEpisode
                episodes.add(newEpisode(epsUrl) {
                    this.name = epsName
                    this.episode = epsName.filter { it.isDigit() }.toIntOrNull()
                })
            }
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = description
                if (scoreVal != null) this.score = Score.from10(scoreVal)
                addTrailer(trailer) // FIX TRAILER
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = description
                if (scoreVal != null) this.score = Score.from10(scoreVal)
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
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            (1..5).forEach { index ->
                try {
                    val response = app.post(
                        ajaxUrl,
                        data = mapOf(
                            "action" to "muvipro_player_content",
                            "tab" to "p$index",
                            "post_id" to postId
                        ),
                        headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to data)
                    ).text

                    val regex = Regex("""["'](https?://[^"']+\.(?:txt|m3u8)[^"']*)["']""")
                    val matches = regex.findAll(response)
                    
                    matches.forEach { match ->
                        val streamUrl = match.groupValues[1]
                        M3u8Helper.generateM3u8(
                            "Klikxxi VIP Tab $index",
                            streamUrl,
                            "https://playerngefilm21.rpmlive.online/",
                            headers = mapOf(
                                "Origin" to "https://playerngefilm21.rpmlive.online",
                                "Referer" to "https://playerngefilm21.rpmlive.online/"
                            )
                        ).forEach { link -> callback(link) }
                    }

                    val ajaxDoc = org.jsoup.Jsoup.parse(response)
                    ajaxDoc.select("iframe").forEach { iframe ->
                        var src = iframe.attr("src")
                        if (src.startsWith("//")) src = "https:$src"
                        loadExtractor(src, data, subtitleCallback, callback)
                    }
                } catch (e: Exception) { }
            }
        }

        document.select(".gmr-download-list a").forEach { link ->
            loadExtractor(link.attr("href"), data, subtitleCallback, callback)
        }
        return true
    }
}
