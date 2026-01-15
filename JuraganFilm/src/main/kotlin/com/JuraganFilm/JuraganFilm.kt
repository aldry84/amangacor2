package com.JuraganFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLDecoder

class JuraganFilm : MainAPI() {

    // ==============================
    // KONFIGURASI UTAMA
    // ==============================
    override var mainUrl = "https://tv41.juragan.film"
    override var name = "JuraganFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    // Header untuk menipu server agar mengira kita adalah browser HP
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"
    )

    // ==============================
    // 1. HALAMAN UTAMA (HOME)
    // ==============================
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, headers = commonHeaders).document
        
        // Mengambil daftar film dari container ID 'gmr-main-load'
        val homeItems = document.select("div#gmr-main-load article").mapNotNull {
            toSearchResult(it)
        }

        return newHomePageResponse(
            list = HomePageList(
                name = "Film Terbaru",
                list = homeItems,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    // ==============================
    // 2. PENCARIAN (SEARCH)
    // ==============================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = commonHeaders).document

        return document.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    // Helper: Mengubah HTML menjadi Data Film Cloudstream
    private fun toSearchResult(element: Element): SearchResponse? {
        val titleElement = element.selectFirst("div.item-article a") ?: return null
        
        // Membersihkan judul dari kata "Nonton", "Sub Indo", dll
        val rawTitle = titleElement.text()
        val title = rawTitle.replace(Regex("(?i)Nonton\\s+(Film\\s+)?"), "").trim()
        val href = titleElement.attr("href")

        val posterElement = element.selectFirst("div.content-thumbnail img")
        val posterUrl = posterElement?.let { img ->
            img.attr("data-src").ifEmpty { img.attr("src") }
        }

        // Cek apakah Serial (ada EPS/Season) atau Film
        val isSeries = element.text().contains("EPS", ignoreCase = true) || 
                       rawTitle.contains("Season", ignoreCase = true)

        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    // ==============================
    // 3. MEMUAT DETAIL (LOAD)
    // ==============================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders).document

        // Judul Bersih
        val rawTitle = document.selectFirst("h1.entry-title")?.text() ?: "No Title"
        val title = rawTitle.replace(Regex("(?i)Nonton\\s+(Film\\s+)?"), "").trim()
        
        // Poster HD
        val poster = document.selectFirst("div.gmr-poster img")?.let { img ->
             img.attr("data-src").ifEmpty { img.attr("src") }
        } ?: document.selectFirst("img.wp-post-image")?.attr("src")

        val description = document.select("div.entry-content p").text()
        val year = document.select("span.year").text().filter { it.isDigit() }.toIntOrNull()

        // List Episode (Untuk Series)
        val episodes = mutableListOf<Episode>()
        val episodeElements = document.select("div.gmr-listseries a")
        
        if (episodeElements.isNotEmpty()) {
            episodeElements.forEach { ep ->
                val epTitle = ep.text()
                val epUrl = ep.attr("href")
                episodes.add(newEpisode(epUrl) {
                    this.name = epTitle
                })
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
    // 4. LOAD LINKS (VIDEO) - FINAL FIX
    // ==============================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, headers = commonHeaders).document

        // Loop semua iframe di halaman detail film
        document.select("iframe").forEach { iframe ->
            var src = iframe.attr("src")
            if (src.startsWith("//")) src = "https:$src"
            
            // --- SKENARIO 1: LINK UTAMA (JURAGAN.INFO) ---
            if (src.contains("juragan.info") || src.contains("juraganfilm")) {
                // Fetch halaman iframe perantara
                val iframeHeaders = mapOf("Referer" to data)
                val iframeDoc = app.get(src, headers = iframeHeaders).document

                // 1.A: Cari Link Cloudbeta (Video Utama)
                val innerIframe = iframeDoc.select("iframe[src*='cloudbeta'], iframe[src*='url=']").firstOrNull()
                val innerSrc = innerIframe?.attr("src")

                if (innerSrc != null) {
                    val rawUrl = innerSrc.substringAfter("url=").substringBefore("&")
                    val decodedUrl = URLDecoder.decode(rawUrl, "UTF-8")

                    if (decodedUrl.contains(".m3u8")) {
                        val videoHeaders = mapOf(
                            "Origin" to "https://juragan.info",
                            "Referer" to "https://juragan.info/",
                            "User-Agent" to commonHeaders["User-Agent"]!!
                        )

                        // METODE 1: M3u8Helper (Standard)
                        // Menggunakan 'source' (sesuai update terbaru Cloudstream)
                        M3u8Helper.generateM3u8(
                            source = "JuraganFilm (VIP)", 
                            streamUrl = decodedUrl,
                            referer = "https://juragan.info/",
                            headers = videoHeaders
                        ).forEach(callback)

                        // METODE 2: Direct Fallback (Cadangan jika M3u8Helper menolak link)
                        // Ini akan muncul sebagai "JuraganFilm (Direct)"
                        callback.invoke(
                            ExtractorLink(
                                source = "JuraganFilm (Direct)",
                                name = "JuraganFilm (Direct)",
                                url = decodedUrl,
                                referer = "https://juragan.info/",
                                quality = Qualities.Unknown.value,
                                isM3u8 = true,
                                headers = videoHeaders
                            )
                        )
                    }
                }

                // 1.B: Cari Link GDrivePlayer (Cadangan)
                iframeDoc.select("iframe[src*='gdriveplayer.to']").forEach { gdIframe ->
                    val gdSrc = gdIframe.attr("src")
                    loadExtractor(gdSrc, "https://juragan.info/", subtitleCallback, callback)
                }
            }
            
            // --- SKENARIO 2: LINK VIDEO UMUM ---
            else if (!src.contains("facebook") && !src.contains("sbani") && !src.contains("histats")) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        
        return true
    }
}
