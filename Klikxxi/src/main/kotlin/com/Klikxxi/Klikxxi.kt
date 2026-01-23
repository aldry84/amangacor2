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

    private fun String?.toLargeUrl(): String? {
        val url = this ?: return null
        val fullUrl = if (url.startsWith("//")) "https:$url" else url
        return fullUrl.replace(Regex("-\\d+x\\d+"), "")
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")
        val imgElement = this.selectFirst("img")
        val rawPoster = imgElement?.attr("data-lazy-src") ?: imgElement?.attr("src")
        val posterUrl = rawPoster.toLargeUrl()
        val quality = this.selectFirst(".gmr-quality-item")?.text() ?: "N/A"

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val homeSets = ArrayList<HomePageList>()

        document.select(".muvipro-posts-module").forEach { widget ->
            val title = widget.select(".homemodule-title").text().trim()
            val movies = widget.select(".gmr-item-modulepost").mapNotNull { it.toSearchResponse() }
            if (movies.isNotEmpty()) {
                homeSets.add(HomePageList(title, movies))
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
        
        // Logika TV Series
        val isTvSeries = document.select(".gmr-numbeps").isNotEmpty() || url.contains("/tv/")
        val tvType = if (isTvSeries) TvType.TvSeries else TvType.Movie

        if (isTvSeries) {
            // Nanti bisa ditambahkan logika episode extractor jika ada HTML contohnya
            return newTvSeriesLoadResponse(title, url, tvType, emptyList()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            return newMovieLoadResponse(title, url, tvType, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // 1. Ambil Post ID
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")

        // 2. Tembak AJAX untuk Server 1-6
        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            // Loop tab p1 sampai p6
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

                    // Parse hasil AJAX
                    val ajaxDoc = org.jsoup.Jsoup.parse(response)
                    ajaxDoc.select("iframe").forEach { iframe ->
                        var src = iframe.attr("src")
                        if (src.startsWith("//")) src = "https:$src"
                        
                        // Cek jika link adalah strp2p (server utama)
                        if (src.contains("strp2p") || src.contains("auvexiug")) {
                             // Untuk saat ini kita load saja, biarkan Cloudstream mencoba menghandlenya
                             loadExtractor(src, data, subtitleCallback, callback)
                        } else if (!src.contains("facebook") && !src.contains("whatsapp")) {
                            loadExtractor(src, data, subtitleCallback, callback)
                        }
                    }
                } catch (e: Exception) {
                    // Skip error
                }
            }
        }

        // 3. Fallback: Cari link download (Lulu, Voe, UserDrive)
        document.select(".gmr-download-list a").forEach { link ->
            val href = link.attr("href")
            loadExtractor(href, data, subtitleCallback, callback)
        }
        
        // 4. Fallback Terakhir: Iframe di halaman utama (jika ada)
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
