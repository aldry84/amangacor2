package com.JavHey

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JavHey : MainAPI() { // Nama class disesuaikan dengan nama file JavHey.kt
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    // --- MAIN PAGE CONFIGURATION ---
    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-baru/page=" to "Paling Baru",
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/videos/jav-sub-indo/page=" to "JAV Sub Indo"
    )

    override suspend fun mainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val document = app.get(url).document
        
        val home = document.select("div.article_standard_view article.item").mapNotNull {
            it.toSearchResult()
        }
        
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val document = app.get(url).document
        
        return document.select("div.article_standard_view article.item").mapNotNull {
            it.toSearchResult()
        }
    }

    // Helper untuk mengubah elemen HTML menjadi SearchResponse
    private fun Element.toSearchResult(): SearchResponse? {
        val header = this.selectFirst("div.item_header") ?: return null
        val content = this.selectFirst("div.item_content") ?: return null
        
        val linkElement = header.selectFirst("a") ?: return null
        val href = fixUrl(linkElement.attr("href"))
        
        val imgElement = header.selectFirst("img")
        val posterUrl = imgElement?.attr("src") ?: imgElement?.attr("data-src")

        val titleElement = content.selectFirst("h3 a") ?: return null
        val title = titleElement.text().trim()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    // --- LOAD DETAIL PAGE (METADATA) ---
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // 1. Ambil Judul
        val title = document.selectFirst("h1.product_title")?.text()?.trim() 
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: "Unknown Title"

        // 2. Ambil Poster (Resolusi tinggi dari class magnificPopupImage)
        val poster = document.selectFirst(".images a.magnificPopupImage")?.attr("href")
            ?: document.selectFirst(".images img")?.attr("src")

        // 3. Ambil Deskripsi
        val description = document.selectFirst(".video-description")?.text()?.replace("Description: ", "")?.trim()
            ?: document.select("meta[name=description]").attr("content")

        // 4. Ambil Tags, Categories, dan Actors
        val tags = document.select(".product_meta a[href*='/tag/'], .product_meta a[href*='/category/']").map { it.text() }
        val actors = document.select(".product_meta a[href*='/actor/']").map { it.text() }

        // 5. Ambil Tahun Rilis
        // Format text: "Release Day: 2024-07-05"
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

    // --- LOAD LINKS (EXTRACTOR) ---
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // METODE 1: Decode Base64 dari input hidden id="links"
        // Value di HTML: <input type="hidden" id="links" value="aHR0cHM6Ly...">
        val hiddenLinks = document.selectFirst("#links")?.attr("value")
        
        if (!hiddenLinks.isNullOrEmpty()) {
            try {
                // Decode Base64
                val decodedString = String(Base64.decode(hiddenLinks, Base64.DEFAULT))
                
                // Split berdasarkan ",,," sesuai pola data
                val urls = decodedString.split(",,,")
                
                urls.forEach { rawUrl ->
                    val url = rawUrl.trim()
                    if (url.isNotEmpty() && url.startsWith("http")) {
                        loadExtractor(url, callback, subtitleCallback)
                    }
                }
            } catch (e: Exception) {
                // Jika decode gagal, lanjut ke metode backup
                e.printStackTrace()
            }
        }

        // METODE 2: Backup (Ambil dari tombol Download di bawah player)
        // Tombol class .links-download a
        document.select(".links-download a").forEach { link ->
            val href = link.attr("href")
            if (href.isNotEmpty()) {
                loadExtractor(href, callback, subtitleCallback)
            }
        }

        return true
    }
}
