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

    // Konfigurasi Halaman Utama
    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-baru/page=" to "Paling Baru",
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/videos/jav-sub-indo/page=" to "JAV Sub Indo"
    )

    // Fungsi Pengambil Halaman Utama
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse? {
        val url = request.data + page
        val doc = app.get(url).document
        val home = doc.select("article.item").mapNotNull {
            toSearchResult(it)
        }
        return newHomePageResponse(request.name, home)
    }

    // Helper: Mengubah HTML menjadi SearchResponse
    private fun toSearchResult(element: Element): SearchResponse? {
        val header = element.selectFirst("div.item_header") ?: return null
        val content = element.selectFirst("div.item_content") ?: return null
        
        val linkElement = header.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        
        val imgElement = header.selectFirst("img")
        // Support lazy loading images
        val posterUrl = imgElement?.attr("src") ?: imgElement?.attr("data-src")

        val titleElement = content.selectFirst("h3 a") ?: return null
        val title = titleElement.text().trim()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    // Fungsi Pencarian
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val document = app.get(url).document
        
        return document.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    // Fungsi Load Metadata (Detail Film)
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Ambil Judul
        val title = document.selectFirst("h1.product_title")?.text()?.trim() 
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: "Unknown Title"

        // Ambil Poster Resolusi Tinggi
        val poster = document.selectFirst(".images a.magnificPopupImage")?.attr("href")
            ?: document.selectFirst(".images img")?.attr("src")

        // Ambil Deskripsi
        val description = document.selectFirst(".video-description")?.text()?.replace("Description: ", "")?.trim()
            ?: document.select("meta[name=description]").attr("content")

        // Ambil Tags
        val tags = document.select(".product_meta a[href*='/tag/'], .product_meta a[href*='/category/']").map { it.text() }
        
        // Ambil Aktor dengan tipe data yang benar
        val actors = document.select(".product_meta a[href*='/actor/']").map { 
            ActorData(Actor(it.text(), null))
        }

        // Ambil Tahun
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

    // Fungsi Load Links (VERSI AGRESIF/FULL SCAN)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val html = document.html() // Ambil seluruh source code HTML untuk scanning regex

        // METODE 1: Decode Base64 Hidden Input (Prioritas Utama)
        val hiddenLinks = document.selectFirst("#links")?.attr("value")
        if (!hiddenLinks.isNullOrEmpty()) {
            try {
                val decodedString = String(Base64.decode(hiddenLinks, Base64.DEFAULT))
                // Pisahkan berdasarkan ",,,"
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

        // METODE 2: Scan Tombol Download & Link Streaming Umum
        // Mencari semua link yang mungkin berisi host video
        val downloadSelectors = ".links-download a, a.btn, .download a, a[href*='streamwish'], a[href*='dood'], a[href*='vidhide']"
        document.select(downloadSelectors).forEach { link ->
            val href = link.attr("href")
            if (href.isNotEmpty() && href.startsWith("http") && !href.contains("javascript")) {
                loadExtractor(href, subtitleCallback, callback)
            }
        }

        // METODE 3: Scan Iframe Langsung
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && src.startsWith("http")) {
                loadExtractor(src, subtitleCallback, callback)
            }
        }

        // METODE 4: Brute Force Regex (Jurus Terakhir)
        // Mencari pola URL host populer di seluruh halaman (termasuk di dalam <script>)
        val regex = Regex("""https?://(streamwish|dood|d000d|vidhide|vidhidepro|mixdrop|filelions|voe|streamtape)[\w./?=&%-]+""")
        regex.findAll(html).forEach { match ->
            val url = match.value
            loadExtractor(url, subtitleCallback, callback)
        }

        return true
    }
}
