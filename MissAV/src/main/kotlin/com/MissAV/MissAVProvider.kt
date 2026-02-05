package com.MissAv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class MissAVProvider : MainAPI() {
    override var mainUrl              = "https://missav.ws"
    override var name                 = "MissAV"
    override val hasMainPage          = true
    override var lang                 = "id" // PENTING: Pakai 'id' sesuai region kamu
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded
    
    private val subtitleCatUrl = "https://www.subtitlecat.com"

    // --- KUNCI RAHASIA (BONGKAR PROTEKSI) ---
    // Kita menyamar sebagai Googlebot. 
    // Server akan tertipu dan mengirim HTML lengkap, bukan Skeleton kosong/API Recombee.
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
            "/dm514/id/new" to "Terbaru",
            "/dm588/id/release" to "Rilis Baru",
            "/dm291/id/today-hot" to "Populer Hari Ini",
            "/dm169/id/weekly-hot" to "Populer Minggu Ini",
            "/dm256/id/monthly-hot" to "Populer Bulan Ini",
            "/dm97/id/fc2" to "FC2 Tanpa Sensor",
            "/dm34/id/madou" to "Madou AV",
            "/dm628/id/uncensored-leak" to "Bocoran Tanpa Sensor",
            "/id/klive" to "Korean Live AV"
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            val url = "$mainUrl${request.data}?page=$page"
            // Gunakan headers Googlebot
            val document = app.get(url, headers = headers).document
            val responseList  = document.select(".thumbnail, div.grid > div").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = true), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // --- LOGIKA EKSTRAKSI ANTI-ERROR ---
        
        // 1. Cari elemen judul dengan berbagai kemungkinan selector
        val titleElement = this.selectFirst("a.text-secondary, a.text-base, h4 a, div.text-secondary")
        val img = this.selectFirst("img")
        
        var title = titleElement?.text()?.trim() ?: ""
        val href = titleElement?.attr("href") ?: this.selectFirst("a")?.attr("href")

        // Jika tidak ada link, skip (mungkin iklan)
        if (href.isNullOrBlank()) return null
        
        // 2. JURUS CADANGAN: Jika text judul kosong, AMBIL DARI ALT GAMBAR
        // Googlebot biasanya selalu dikasih atribut alt yang lengkap pada gambar.
        if (title.isBlank()) {
            title = img?.attr("alt")?.trim() ?: ""
        }
        
        // Kalau masih kosong, skip
        if (title.isBlank()) return null

        // 3. Tambahkan status [Uncensored] jika ada
        val status = this.selectFirst(".bg-blue-800, .bg-red-800")?.text()?.trim()
        if (!status.isNullOrEmpty() && !title.contains(status)) {
            title = "[$status] $title"
        }
        
        // 4. LOGIKA GAMBAR: Prioritas data-src (lazy load), fallback ke src
        // Ini mengatasi error NullRequestDataException di log kamu
        val posterUrl = img?.attr("data-src")?.ifBlank { img.attr("src") }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val cleanQuery = query.trim()

        // --- BYPASS PENCARIAN (DIRECT ACCESS) ---
        // Jika user mencari Kode (misal: SHKD-451 atau JUX-123), LANGSUNG BUKA halaman video.
        // Ini menghindari halaman search yang sering error/kosong.
        val codeRegex = Regex("^[a-zA-Z]+-\\d+$") 
        if (cleanQuery.matches(codeRegex)) {
            try {
                // Tembak langsung URL video
                val directUrl = "$mainUrl/id/${cleanQuery.lowercase()}"
                val doc = app.get(directUrl, headers = headers).document
                
                // Ambil data dari Meta Tags (Pasti Lengkap karena Server-Side Rendered untuk Googlebot)
                val metaTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
                val metaImg = doc.selectFirst("meta[property=og:image]")?.attr("content")
                val metaDesc = doc.selectFirst("meta[property=og:description]")?.attr("content")
                
                if (!metaTitle.isNullOrEmpty()) {
                    searchResponse.add(
                        newMovieSearchResponse(metaTitle, directUrl, TvType.NSFW) {
                            this.posterUrl = metaImg
                            this.plot = metaDesc
                        }
                    )
                    // Sukses! Langsung return tanpa scraping halaman search yang berat
                    return searchResponse
                }
            } catch (e: Exception) {
                // Kalau gagal (mungkin kode salah), lanjut ke cara biasa
            }
        }

        // --- PENCARIAN BIASA (GOOGLEBOT MODE) ---
        // Untuk kata kunci biasa seperti "Roe"
        val searchUrl = "$mainUrl/id/search/$cleanQuery"
        
        for (i in 1..2) { // Cukup 2 halaman agar tidak berat
            try {
                val url = "$searchUrl?page=$i"
                val document = app.get(url, headers = headers).document
                
                val results = document.select(".thumbnail, div.grid > div").mapNotNull { it.toSearchResult() }

                if (results.isNotEmpty()) {
                    searchResponse.addAll(results)
                } else {
                    break
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return searchResponse.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "No Title"
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        // Header Googlebot penting di sini juga agar tidak diblokir saat ambil source
        val response = app.get(data, headers = headers)
        val doc = response.document
        
        try {
            getAndUnpack(response.text).let { unpackedText ->
                val m3u8Regex = Regex("""source\s*=\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
                val match = m3u8Regex.find(unpackedText)
                val m3u8Url = match?.groupValues?.get(1)

                if (m3u8Url != null) {
                    callback.invoke(
                        newExtractorLink(name, "$name HLS", m3u8Url, ExtractorLinkType.M3U8) {
                            this.referer = data
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // SUBTITLE LOGIC
        try {
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            val javCode = Regex("([a-zA-Z]+-\\d+)").find(title)?.groupValues?.get(1)
            
            if (!javCode.isNullOrEmpty()) {
                val query = "$subtitleCatUrl/index.php?search=$javCode"
                // SubtitleCat butuh User-Agent browser biasa (Android), JANGAN pakai Googlebot di sini
                // karena situs subtitle mungkin memblokir bot.
                val subHeaders = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                
                val subDoc = app.get(query, headers = subHeaders, timeout = 20).document 
                
                val subList = subDoc.select("td a")
                for (item in subList.take(3)) {
                    if (item.text().contains(javCode, ignoreCase = true)) {
                        val fullUrl = "$subtitleCatUrl/${item.attr("href")}"
                        val pDoc = app.get(fullUrl, headers = subHeaders, timeout = 10).document
                        val sList = pDoc.select(".col-md-6.col-lg-4")
                        
                        for (subItem in sList) {
                            try {
                                val language = subItem.select(".sub-single span:nth-child(2)").text()
                                val downloadLinkInfo = subItem.select(".sub-single span:nth-child(3) a").firstOrNull()

                                if (downloadLinkInfo != null && downloadLinkInfo.text() == "Download") {
                                    val url = "$subtitleCatUrl${downloadLinkInfo.attr("href")}"
                                    val langName = language.replace(Regex("[^a-zA-Z ]"), "").trim()

                                    subtitleCallback.invoke(
                                        SubtitleFile(langName, url)
                                    )
                                }
                            } catch (e: Exception) { }
                        }
                    }
                }
            }
        } catch (e: Exception) { }

        return true
    }
}
