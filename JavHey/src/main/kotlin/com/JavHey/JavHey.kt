package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

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

    // ==========================================
    // PERBAIKAN BAGIAN DETAIL VIDEO
    // ==========================================
    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        // Menggunakan Meta Tags (Lebih Aman & Akurat)
        val title = doc.select("meta[property='og:title']").attr("content").trim()
            .ifEmpty { doc.select("h1").text().trim() } // Backup kalau meta kosong
        
        val poster = doc.select("meta[property='og:image']").attr("content")
        
        val description = doc.select("meta[name='description']").attr("content")
            .ifEmpty { doc.select("div.video-description").text() }

        // Bersihkan judul dari tulisan "JAV Subtitle Indonesia -" biar rapi
        val cleanTitle = title.replace("JAV Subtitle Indonesia -", "").trim()

        return newMovieLoadResponse(cleanTitle, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ==========================================
    // PERBAIKAN BAGIAN LINK STREAMING
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        // Cari iframe player
        // Biasanya: <iframe src="https://bysebuho.com/e/...">
        val iframeSrc = doc.select("iframe").attr("src")
        
        if (iframeSrc.contains("bysebuho")) {
            invokeBysebuho(iframeSrc, subtitleCallback, callback)
            return true
        }
        
        return false
    }

    private suspend fun invokeBysebuho(
        iframeUrl: String, 
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // iframeUrl contoh: https://bysebuho.com/e/wj3t2az4zpal/jur-547...
        // Kita butuh ID: wj3t2az4zpal
        val code = iframeUrl.substringAfter("/e/").substringBefore("/")
        
        // API URL sesuai cURL kamu
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
            
            // Link dari JSON: "https://9n8o.com/f143m/wj3t2az4zpal"
            val nextUrl = json.embed_frame_url
            
            if (!nextUrl.isNullOrEmpty()) {
                // Cloudstream akan otomatis menangani 9n8o.com (biasanya redirect ke m3u8)
                loadExtractor(nextUrl, subtitleCallback, callback)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    data class BysebuhoResponse(
        val embed_frame_url: String? = null
    )
}
