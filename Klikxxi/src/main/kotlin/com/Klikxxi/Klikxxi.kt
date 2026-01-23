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

        val imgElement = this.selectFirst("img")
        val rawPoster = imgElement?.attr("data-lazy-src") ?: imgElement?.attr("src")
        val posterUrl = rawPoster.toLargeUrl()

        val quality = this.selectFirst(".gmr-quality-item")?.text() ?: "N/A"

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    // --- MAIN FUNCTIONS ---

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        
        // 1. Ambil Halaman Utama Asli (Latest Movies & TV Series)
        // Kita pakai try-catch agar jika satu gagal, yang lain tetap jalan
        try {
            val document = app.get(mainUrl).document
            document.select(".muvipro-posts-module").forEach { widget ->
                val title = widget.select(".homemodule-title").text().trim()
                val movies = widget.select(".gmr-item-modulepost").mapNotNull { it.toSearchResponse() }
                if (movies.isNotEmpty()) items.add(HomePageList(title, movies))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Tambahkan 5 Kategori Pilihan (Action, Horror, Drama, Comedy, Asia)
        // Struktur halaman kategori SAMA dengan halaman Search (article.item)
        val customCategories = listOf(
            Pair("Action Movies", "$mainUrl/category/action/"),
            Pair("Horror Movies", "$mainUrl/category/horror/"),
            Pair("Drama Movies", "$mainUrl/category/drama/"),
            Pair("Comedy Movies", "$mainUrl/category/comedy/"),
            Pair("Asian Movies", "$mainUrl/category/asia/")
        )

        // Kita fetch secara paralel (apmap) supaya loadingnya cepat
        customCategories.apmap { (title, url) ->
            try {
                val doc = app.get(url).document
                val movies = doc.select("article.item").mapNotNull { it.toSearchResponse() }
                
                if (movies.isNotEmpty()) {
                    synchronized(items) {
                        items.add(HomePageList(title, movies))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type%5B%5D=post&post_type%5B%5D=tv"
        val document = app.get(url).document

        return document.select("article.item").mapNotNull {
            it.toSearchResponse()
        }
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

        val isTvSeries = document.select(".gmr-numbeps").isNotEmpty() || url.contains("/tv/")
        val tvType = if (isTvSeries) TvType.TvSeries else TvType.Movie

        return if (isTvSeries) {
            newTvSeriesLoadResponse(title, url, tvType, emptyList()) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, tvType, url) {
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

        // 1. Cek AJAX Player (Prioritas Utama)
        val postId = document.selectFirst("#muvipro_player_content_id")?.attr("data-id")
        if (postId != null) {
            val ajaxUrl = "$mainUrl/wp-admin/admin-ajax.php"
            // Loop tab player 1 sampai 5
            (1..5).map { index ->
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
                        
                        if (!src.contains("facebook") && !src.contains("whatsapp")) {
                            loadExtractor(src, data, subtitleCallback, callback)
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        // 2. Cek Link Download (Fallback)
        document.select(".gmr-download-list a").forEach { link ->
            val href = link.attr("href")
            loadExtractor(href, data, subtitleCallback, callback)
        }
        
        // 3. Cek Iframe Biasa (Fallback Terakhir)
        document.select("iframe").forEach { iframe ->
             var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
             if (!src.contains("facebook") && !src.contains("whatsapp") && src.startsWith("http")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
