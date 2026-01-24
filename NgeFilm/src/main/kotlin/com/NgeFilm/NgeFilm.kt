package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NgeFilm : ParsableHttpProvider() {
    override var mainUrl = "https://new31.ngefilm.site"
    override var name = "NgeFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ==============================
    // 1. HALAMAN UTAMA (HOME)
    // ==============================
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Terbaru",
        // Kamu bisa menambahkan halaman lain nanti, misalnya:
        // "$mainUrl/populer" to "Populer"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        // Mengambil daftar film dari halaman utama
        val home = document.select("#gmr-main-load article.item-infinite").mapNotNull {
            toSearchResult(it)
        }
        return newHomePageResponse(request.name, home)
    }

    // ==============================
    // 2. PENCARIAN (SEARCH)
    // ==============================
    override suspend fun search(query: String): List<SearchResponse> {
        // Menambahkan parameter post_type[] agar Film & TV Series muncul semua dalam pencarian
        val url = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(url).document
        
        return document.select("#gmr-main-load article.item-infinite").mapNotNull {
            toSearchResult(it)
        }
    }

    // ==============================
    // 3. DETAIL FILM (LOAD)
    // ==============================
    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        // Mengambil Metadata Film
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = document.selectFirst(".gmr-movie-view .attachment-thumbnail")?.attr("src")
        val plot = document.select(".entry-content p").text()
        
        // Mengambil Tahun & Rating
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()
        val rating = document.selectFirst(".gmr-rating-item span")?.text()?.toRatingInt()
        
        // Mengambil Genre & Aktor
        val tags = document.select(".gmr-movie-on a[rel='category tag']").map { it.text() }
        val actors = document.select("[itemprop='actor'] span[itemprop='name']").map { it.text() }

        // Mengambil Rekomendasi Film (Related Posts)
        val recommendations = document.select("#gmr-related-post article").mapNotNull {
            toSearchResult(it)
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = plot
            this.tags = tags
            this.rating = rating
            this.actors = actors
            this.recommendations = recommendations
        }
    }

    // ==============================
    // 4. MEMUTAR VIDEO (LINKS)
    // ==============================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Mencari iframe di dalam kotak player (class .gmr-embed-responsive)
        document.select("div.gmr-embed-responsive iframe").forEach { iframe ->
            var sourceUrl = iframe.attr("src")
            
            // Perbaikan URL jika formatnya protokol relatif (diawali //)
            if (sourceUrl.startsWith("//")) {
                sourceUrl = "https:$sourceUrl"
            }

            // Cloudstream akan otomatis mencoba mengekstrak video dari URL tersebut
            // (misalnya jika linknya dari streamwish, doodstream, atau player bawaan mereka)
            if (sourceUrl.isNotBlank()) {
                loadExtractor(sourceUrl, callback, subtitleCallback)
            }
        }

        return true
    }

    // ==============================
    // FUNGSI BANTUAN (HELPER)
    // ==============================
    private fun toSearchResult(element: Element): SearchResponse? {
        // Mengambil Judul & Link
        val titleElement = element.selectFirst(".entry-title a") ?: return null
        val title = titleElement.text().replace("Nonton ", "").trim()
        val url = fixUrl(titleElement.attr("href"))
        
        // Mengambil Poster
        val imgElement = element.selectFirst(".content-thumbnail img")
        // Prioritas ambil src, kalau tidak ada ambil data-src (lazy load)
        val posterUrl = imgElement?.attr("src") ?: imgElement?.attr("data-src")
        
        // Mengambil Kualitas (WEB-DL, HD, dll)
        val quality = element.selectFirst(".gmr-quality-item a")?.text()

        return newMovieSearchResponse(title, url, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }
}
