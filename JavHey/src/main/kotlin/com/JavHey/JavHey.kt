package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

// Nama class disesuaikan dengan nama file: JavHey
class JavHey : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)

    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-baru/page=" to "Paling Baru",
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/videos/jav-sub-indo/page=" to "JAV Sub Indo"
    )

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

    private fun toSearchResult(element: Element): SearchResponse? {
        val title = element.selectFirst("h3 > a")?.text()?.trim() 
            ?: element.selectFirst("img")?.attr("alt") 
            ?: return null
        val href = element.selectFirst("div.item_header > a")?.attr("href") ?: return null
        val posterUrl = element.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val doc = app.get(url).document
        return doc.select("article.item").mapNotNull {
            toSearchResult(it)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Unknown Title"
        val poster = doc.selectFirst("div.video_player img")?.attr("src") 
            ?: doc.selectFirst("article.item img")?.attr("src")
        val description = doc.select("div.video-description").text()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        val iframeSrc = doc.select("iframe").attr("src")
        
        if (iframeSrc.contains("bysebuho")) {
            invokeBysebuho(iframeSrc, callback)
            return true
        }
        return false
    }

    private suspend fun invokeBysebuho(iframeUrl: String, callback: (ExtractorLink) -> Unit) {
        val code = iframeUrl.substringAfter("/e/").substringBefore("/")
        val apiUrl = "https://bysebuho.com/api/videos/$code/embed/details"
        
        val headers = mapOf(
            "Referer" to iframeUrl,
            "x-embed-origin" to mainUrl,
            "x-embed-parent" to iframeUrl,
            "x-embed-referer" to mainUrl
        )

        try {
            val jsonText = app.get(apiUrl, headers = headers).text
            val json = parseJson<BysebuhoResponse>(jsonText)
            val nextUrl = json.embed_frame_url
            
            if (!nextUrl.isNullOrEmpty()) {
                loadExtractor(nextUrl, callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class BysebuhoResponse(
        val embed_frame_url: String? = null
    )
}
