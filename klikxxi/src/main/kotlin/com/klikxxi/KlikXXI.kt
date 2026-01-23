package com.klikxxi

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class KlikXXI : MainAPI() {
    override var mainUrl = "https://klikxxi.me"
    override var name = "KlikXXI"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // --- HELPER FUNCTIONS ---

    // Mengubah elemen HTML menjadi SearchResponse (Data Film di list)
    private fun Element.toSearchResponse(): SearchResponse? {
        // Mengambil judul dan link dari class 'entry-title'
        val titleElement = this.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href")

        // Mengambil poster
        // Prioritas: data-lazy-src (karena pakai RocketLoader) -> src biasa
        val posterElement = this.selectFirst("img.attachment-medium")
        val posterUrl = posterElement?.attr("data-lazy-src") ?: posterElement?.attr("src")

        // Mengambil kualitas (HD/CAM)
        val quality = this.selectFirst(".gmr-quality-item")?.text() ?: "N/A"

        // Mengambil Rating (Perbaikan Error 1 & 2)
        // Kita parse manual text-nya menjadi Double, lalu dikali 10 menjadi Int (skala 0-100)
        // Contoh text: "8" -> 8.0 -> 80
        val ratingElement = this.selectFirst(".gmr-rating-item")?.text()?.trim()
        val ratingValue = ratingElement?.toDoubleOrNull()?.times(10)?.toInt()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
            // Ganti 'apiRating' menjadi 'rating'
            if (ratingValue != null) {
                this.rating = ratingValue
            }
        }
    }

    // --- MAIN FUNCTIONS ---

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Mengambil HTML dari halaman utama
        val document = app.get(mainUrl).document
        val homeSets = ArrayList<HomePageList>()

        // Loop setiap widget kategori di homepage
        document.select(".muvipro-posts-module").forEach { widget ->
            val title = widget.select(".homemodule-title").text().trim()
            
            // Ambil semua film dalam widget tersebut
            val movies = widget.select(".gmr-item-modulepost").mapNotNull { 
                it.toSearchResponse() 
            }

            if (movies.isNotEmpty()) {
                homeSets.add(HomePageList(title, movies))
            }
        }

        // Perbaikan Error 3: Menggunakan 'newHomePageResponse' alih-alih constructor langsung
        return newHomePageResponse(homeSets)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url).document

        return document.select(".gmr-item-modulepost").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        
        val poster = document.selectFirst("img.attachment-thumbnail")?.attr("src") 
                     ?: document.selectFirst(".gmr-poster img")?.attr("src")
                     ?: document.selectFirst("figure img")?.attr("src")

        val description = document.select(".entry-content p").text().trim()
        
        val year = document.select("time[itemprop=dateCreated]").text().takeLast(4).toIntOrNull()

        val isTvSeries = document.select(".gmr-numbeps").isNotEmpty() || url.contains("/tv/")
        val tvType = if (isTvSeries) TvType.TvSeries else TvType.Movie

        if (isTvSeries) {
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

        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) {
                src = "https:$src"
            }

            if (src.contains("youtube") || src.contains("facebook") || src.contains("whatsapp")) {
                return@forEach
            }

            loadExtractor(src, data, subtitleCallback, callback)
        }

        return true
    }
}
