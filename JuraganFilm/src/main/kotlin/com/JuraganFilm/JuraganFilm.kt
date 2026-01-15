package com.juraganfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JuraganFilm : MainAPI() {

    override var mainUrl = "https://tv41.juragan.film"
    override var name = "JuraganFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // ==============================
    // 1. HOME
    // ==============================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, headers = commonHeaders).document
        val homeItems = document.select("div#gmr-main-load article").mapNotNull {
            toSearchResult(it)
        }
        return newHomePageResponse(
            list = HomePageList(name = "Film Terbaru", list = homeItems, isHorizontalImages = false),
            hasNext = false
        )
    }

    // ==============================
    // 2. SEARCH
    // ==============================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = commonHeaders).document
        return document.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("div.item-article a") ?: return null
        val title = titleElement.text().replace(Regex("(?i)Nonton\\s+"), "").trim() // Hapus kata "Nonton" biar rapi
        val href = titleElement.attr("href")
        val posterUrl = element.selectFirst("div.content-thumbnail img")?.let { img ->
            img.attr("data-src").ifEmpty { img.attr("src") }
        }

        val isSeries = element.text().contains("EPS", ignoreCase = true) || title.contains("Season", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    // ==============================
    // 3. LOAD (DETAIL)
    // ==============================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders).document

        // Bersihkan judul dari kata "Nonton Film" dsb
        val rawTitle = document.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val title = rawTitle.replace(Regex("(?i)Nonton\\s+(Film\\s+)?"), "").trim()

        val poster = document.selectFirst("div.gmr-poster img")?.let { img ->
             img.attr("data-src").ifEmpty { img.attr("src") }
        } ?: document.selectFirst("img.wp-post-image")?.attr("src")

        val description = document.select("div.entry-content p").text()
        val year = document.select("span.year").text().filter { it.isDigit() }.toIntOrNull()

        // Cek Series
        val episodes = mutableListOf<Episode>()
        val episodeElements = document.select("div.gmr-listseries a")
        
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { ep ->
                val epTitle = ep.text()
                val epUrl = ep.attr("href")
                episodes.add(newEpisode(epUrl) { this.name = epTitle })
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
            }
        }
    }

    // ==============================
    // 4. LOAD LINKS (VIDEO) - UPDATE PENTING!
    // ==============================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document

        // 1. Ambil video utama (Iframe default)
        document.select("div#muvipro_player_content_id iframe").forEach { iframe ->
            val src = iframe.attr("src")
            loadExtractor(fixUrl(src), data, subtitleCallback, callback)
        }

        // 2. Ambil tombol server (GDRIVE, JUICE, dll)
        // Biasanya ada di tab muvipro-player-tabs
        document.select("ul.muvipro-player-tabs li a").forEach { serverBtn ->
            val serverUrl = serverBtn.attr("href")
            val serverName = serverBtn.text()

            // Jika tombolnya bukan link '#' (link mati), kita load isinya
            if (serverUrl.isNotEmpty() && !serverUrl.contains("#")) {
                 // Terkadang link tombol itu memuat halaman baru/ajax
                 // Untuk simplifikasi, kita coba loadExtractor langsung jika itu link embed
                 loadExtractor(fixUrl(serverUrl), data, subtitleCallback, callback)
            } else {
                // Jika linknya memanggil script (AJAX), kita coba cari iframe di dalam content data-post (Advanced)
                // Untuk tahap ini, kita fokus ke iframe utama dulu.
            }
        }

        // 3. Cadangan: Cari semua iframe di halaman (Metode Sapu Jagat)
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.contains("juragan") || src.contains("google") || src.contains("facebook")) return@forEach // Skip iklan
            loadExtractor(fixUrl(src), data, subtitleCallback, callback)
        }

        return true
    }
    
    // Fungsi kecil untuk benerin URL (misal dari //gdrive... jadi https://gdrive...)
    private fun fixUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        return url
    }
}
