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
    override val supportedTypes = setOf(TvType.NSFW)

    // PERBAIKAN 1: Gunakan 'override var' bukan 'override val'
    override var mainPage = mainPageOf(
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

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.product_title")?.text()?.trim() 
            ?: document.selectFirst("h1")?.text()?.trim() 
            ?: "Unknown Title"

        val poster = document.selectFirst(".images a.magnificPopupImage")?.attr("href")
            ?: document.selectFirst(".images img")?.attr("src")

        val description = document.selectFirst(".video-description")?.text()?.replace("Description: ", "")?.trim()
            ?: document.select("meta[name=description]").attr("content")

        val tags = document.select(".product_meta a[href*='/tag/'], .product_meta a[href*='/category/']").map { it.text() }
        
        // PERBAIKAN 2: Konversi List<String> ke List<ActorData>
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
            this.actors = actors // Sekarang tipe datanya sudah benar
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val hiddenLinks = document.selectFirst("#links")?.attr("value")
        
        if (!hiddenLinks.isNullOrEmpty()) {
            try {
                val decodedString = String(Base64.decode(hiddenLinks, Base64.DEFAULT))
                val urls = decodedString.split(",,,")
                
                urls.forEach { rawUrl ->
                    val url = rawUrl.trim()
                    if (url.isNotEmpty() && url.startsWith("http")) {
                        // PERBAIKAN 3: Menukar posisi callback dan subtitleCallback
                        // Format yang benar: (url, subtitleCallback, callback)
                        loadExtractor(url, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        document.select(".links-download a").forEach { link ->
            val href = link.attr("href")
            if (href.isNotEmpty()) {
                // PERBAIKAN 3: Menukar posisi callback dan subtitleCallback di sini juga
                loadExtractor(href, subtitleCallback, callback)
            }
        }

        return true
    }
}
