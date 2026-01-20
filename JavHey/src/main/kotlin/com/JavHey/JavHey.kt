package com.JavHey

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JavHey : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    // Header Global untuk JavHey (Biar bisa masuk halaman utama)
    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-baru/page=" to "Paling Baru",
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val url = request.data + page
        return try {
            val doc = app.get(url, headers = commonHeaders).document
            val home = doc.select("article.item").mapNotNull {
                toSearchResult(it)
            }
            newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            null
        }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val header = element.selectFirst("div.item_header") ?: return null
        val content = element.selectFirst("div.item_content") ?: return null
        
        val linkElement = header.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        
        val imgElement = header.selectFirst("img")
        var posterUrl = imgElement?.attr("src")
        // Fix untuk lazy load images
        if (posterUrl.isNullOrEmpty() || posterUrl.contains("data:image")) {
            posterUrl = imgElement?.attr("data-src") ?: imgElement?.attr("data-original")
        }

        val titleElement = content.selectFirst("h3 a") ?: return null
        val title = titleElement.text().trim()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        return try {
            val document = app.get(url, headers = commonHeaders).document
            document.select("article.item").mapNotNull {
                toSearchResult(it)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = commonHeaders).document

        val title = document.selectFirst("h1.product_title")?.text()?.trim() 
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: "Unknown Title"

        val poster = document.selectFirst(".images a.magnificPopupImage")?.attr("href")
            ?: document.selectFirst(".images img")?.attr("src")
            ?: document.selectFirst("article.item img")?.attr("src")

        val description = document.selectFirst(".video-description")?.text()
            ?.replace("Description:", "", true)?.trim()
            ?: document.select("meta[name=description]").attr("content")

        val tags = document.select(".product_meta a[href*='/tag/'], .product_meta a[href*='/category/']").map { it.text() }
        
        val actors = document.select(".product_meta a[href*='/actor/']").map { 
            ActorData(Actor(it.text(), null))
        }

        val releaseText = document.selectFirst(".product_meta")?.text() ?: ""
        val yearRegex = Regex("""(\d{4})-\d{2}-\d{2}""")
        val yearMatch = yearRegex.find(releaseText)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
            this.year = year
            this.actors = actors
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Ambil text HTML untuk parsing manual (lebih cepat & hemat memori)
        val text = app.get(data, headers = commonHeaders).text 
        val document = org.jsoup.Jsoup.parse(text)

        val foundUrls = mutableListOf<String>()

        // 1. Decode Base64 Hidden Input
        val hiddenLinks = document.selectFirst("#links")?.attr("value")
        if (!hiddenLinks.isNullOrEmpty()) {
            try {
                val decodedString = String(Base64.decode(hiddenLinks, Base64.DEFAULT))
                foundUrls.addAll(decodedString.split(",,,").map { it.trim() })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Scan Regex Agresif (Mencari semua link host di source code)
        val regex = Regex("""https?://(streamwish|dood|d000d|vidhide|vidhidepro|mixdrop|filelions|voe|streamtape|advertape|myvidplay|lelebakar|bysebuho|minochinos|cavanhabg|kr21|turtle4up)[\w./?=&%-]+""")
        regex.findAll(text).forEach { match ->
            foundUrls.add(match.value)
        }

        // 3. Scan Element HTML
        document.select("iframe[src], .links-download a[href]").forEach { 
            foundUrls.add(it.attr("src").ifEmpty { it.attr("href") }) 
        }

        // PROSES LINK
        foundUrls.distinct().forEach { url ->
            if (url.isNotBlank() && url.startsWith("http")) {
                // HANDLER KHUSUS LELEBAKAR (Sesuai CURL Terbaru)
                if (url.contains("lelebakar.xyz") || url.contains("lelebakar")) {
                    LeleBakarExtractor().getUrl(url, null, subtitleCallback, callback)
                } 
                // HANDLER DEFAULT (Cloudstream Standard)
                else {
                    loadExtractor(url, subtitleCallback, callback)
                }
            }
        }

        return true
    }
}

// --- CUSTOM EXTRACTOR: LELEBAKAR ---
class LeleBakarExtractor : ExtractorApi() {
    override val name = "LeleBakar"
    override val mainUrl = "https://lelebakar.xyz"
    override val requiresReferer = true 

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Header Super Spesifik (Berdasarkan Data Curl User)
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
                "Referer" to url, // Referer wajib halaman embed itu sendiri
                "Origin" to "https://lelebakar.xyz",
                "Accept" to "*/*",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin"
            )

            // 1. Ambil Source Code Halaman Embed
            val response = app.get(url, headers = headers).text
            
            // 2. Cari file .m3u8 menggunakan Regex yang lebih fleksibel
            // Mencari pola: "https://....master.m3u8" atau file m3u8 lainnya
            val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
            val match = m3u8Regex.find(response)
            
            match?.groupValues?.get(1)?.let { m3u8Url ->
                // 3. Kirim Link ke Player dengan Header yang Benar
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        m3u8Url,
                        url, // Referer yang dikirim ke player adalah halaman embed
                        Qualities.Unknown.value,
                        true, // Video tipe HLS (m3u8) - Penting untuk file master.m3u8!
                        headers = headers // Header disematkan ke player agar tidak 403 Forbidden
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
