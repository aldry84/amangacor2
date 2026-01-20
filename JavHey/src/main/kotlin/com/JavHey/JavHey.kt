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

    // PERBAIKAN: Definisi Manual & Eksplisit
    // Kita tidak pakai 'mainPageOf' untuk menghindari kesalahan deteksi tipe oleh compiler.
    // Kita pakai 'listOf' dan konstruktor 'MainPageData' langsung.
    override val mainPage: List<MainPageData> = listOf(
        MainPageData("$mainUrl/videos/paling-baru/page=", "Paling Baru", true),
        MainPageData("$mainUrl/videos/paling-dilihat/page=", "Paling Dilihat", true),
        MainPageData("$mainUrl/videos/top-rating/page=", "Top Rating", true),
        MainPageData("$mainUrl/videos/jav-sub-indo/page=", "JAV Sub Indo", true)
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
        val document = app.get(data).document

        // Decode Base64 Hidden Input
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

        // Backup
        document.select(".links-download a").forEach { link ->
            val href = link.attr("href")
            if (href.isNotEmpty()) {
                loadExtractor(href, subtitleCallback, callback)
            }
        }

        return true
    }
}
