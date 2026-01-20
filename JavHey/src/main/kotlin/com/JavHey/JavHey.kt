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

    // PERBAIKAN 1: Header Lebih Aman (Anti-Cancel)
    // Menghapus 'Sec-Fetch-*' yang sering bentrok dengan WebViewResolver Cloudstream
    // Menambahkan 'Referer' agar tidak ditolak server
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
            null // Mencegah crash jika halaman gagal load
        }
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val header = element.selectFirst("div.item_header") ?: return null
        val content = element.selectFirst("div.item_content") ?: return null
        
        val linkElement = header.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        
        // PERBAIKAN 2: Safe Image Loading (Anti-Coil Crash)
        // Memastikan posterUrl valid sebelum dikirim
        val imgElement = header.selectFirst("img")
        var posterUrl = imgElement?.attr("src")
        
        // Cek lazy load attributes jika src kosong atau placeholder
        if (posterUrl.isNullOrEmpty() || posterUrl.contains("data:image")) {
            posterUrl = imgElement?.attr("data-src") ?: imgElement?.attr("data-original")
        }

        val titleElement = content.selectFirst("h3 a") ?: return null
        val title = titleElement.text().trim()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl // Cloudstream handle null gracefully, as long as logic above is sound
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

        // Ambil poster resolusi tinggi dengan fallback
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
        // PERBAIKAN 3: Network Robustness
        // Menggunakan text langsung untuk mempercepat parsing regex
        val text = app.get(data, headers = commonHeaders).text 
        val document = org.jsoup.Jsoup.parse(text) // Parse manual dari text agar hemat resource

        // METODE 1: Decode Base64 Hidden Input
        val hiddenLinks = document.selectFirst("#links")?.attr("value")
        if (!hiddenLinks.isNullOrEmpty()) {
            try {
                val decodedString = String(Base64.decode(hiddenLinks, Base64.DEFAULT))
                val urls = decodedString.split(",,,")
                urls.forEach { rawUrl ->
                    val url = rawUrl.trim()
                    if (url.isNotEmpty() && url.startsWith("http")) {
                        loadExtractor(url, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // METODE 2: Scan Tombol Download
        val downloadSelectors = ".links-download a, a.btn, .download a, a[href*='streamwish'], a[href*='dood'], a[href*='vidhide']"
        document.select(downloadSelectors).forEach { link ->
            val href = link.attr("href")
            if (href.isNotEmpty() && href.startsWith("http") && !href.contains("javascript")) {
                loadExtractor(href, subtitleCallback, callback)
            }
        }

        // METODE 3: Scan Iframe
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && src.startsWith("http")) {
                loadExtractor(src, subtitleCallback, callback)
            }
        }

        // METODE 4: Brute Force Regex (Host List Lengkap dari analisa HTML terakhir)
        val regex = Regex("""https?://(streamwish|dood|d000d|vidhide|vidhidepro|mixdrop|filelions|voe|streamtape|advertape|myvidplay|lelebakar|bysebuho|minochinos|cavanhabg|kr21|turtle4up)[\w./?=&%-]+""")
        regex.findAll(text).forEach { match ->
            val url = match.value
            loadExtractor(url, subtitleCallback, callback)
        }

        return true
    }
}
