package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class JavHey : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    // Header Global (Wajib ada biar gak dikira bot)
    private val globalHeaders = mapOf(
        "Authority" to "javhey.com",
        "Accept-Language" to "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Mobile Safari/537.36",
        "Sec-Ch-Ua" to "\"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"",
        "Sec-Ch-Ua-Mobile" to "?1",
        "Sec-Ch-Ua-Platform" to "\"Android\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Upgrade-Insecure-Requests" to "1"
    )

    // =========================================================================
    // 1. MAIN PAGE (Daftar Video)
    // =========================================================================
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest Updates",
        "$mainUrl/best/" to "Best Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page, headers = globalHeaders).document
        
        // SELECTOR FINAL: Ambil semua anak langsung dari div.row di dalam container
        val targetElements = document.select("div.container.mt-5 div.row > div")
        
        val home = targetElements.mapNotNull { element ->
            val linkTag = element.selectFirst("a") ?: return@mapNotNull null
            val imgTag = element.selectFirst("img") ?: return@mapNotNull null
            
            val title = linkTag.attr("title").ifEmpty { imgTag.attr("alt") }
            val link = linkTag.attr("href")
            val img = imgTag.attr("src").ifEmpty { imgTag.attr("data-src") }

            if (link.isBlank() || title.isBlank()) return@mapNotNull null

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.NSFW,
                img,
                null
            )
        }
        return newHomePageResponse(request.name, home)
    }

    // =========================================================================
    // 2. SEARCH (Pencarian)
    // =========================================================================
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url, headers = globalHeaders).document

        return document.select("div.container div.row > div").mapNotNull {
            val linkTag = it.selectFirst("a") ?: return@mapNotNull null
            val imgTag = it.selectFirst("img") ?: return@mapNotNull null
            
            val title = linkTag.attr("title")
            val link = linkTag.attr("href")
            val img = imgTag.attr("src").ifEmpty { imgTag.attr("data-src") }

            MovieSearchResponse(
                title,
                link,
                this.name,
                TvType.NSFW,
                img,
                null
            )
        }
    }

    // =========================================================================
    // 3. LOAD (Detail Halaman)
    // =========================================================================
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = globalHeaders).document
        
        val title = document.selectFirst("h1")?.text()?.trim() ?: "No Title"
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content") 
                    ?: document.selectFirst("div.content_banner img")?.attr("src")

        // Ambil link iframe (sumber video)
        val iframeSrc = document.select("iframe").attr("src")

        val plot = document.selectFirst("meta[name=description]")?.attr("content")

        return newMovieLoadResponse(title, url, TvType.NSFW, iframeSrc) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    // =========================================================================
    // 4. LOAD LINKS (Extractor API Bysebuho)
    // =========================================================================
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        // data = link iframe
        
        if (data.contains("bysebuho.com")) {
            val id = data.substringAfter("/e/").substringBefore("/")
            val apiUrl = "https://bysebuho.com/api/videos/$id/embed/details"
            
            val apiHeaders = mapOf(
                "Authority" to "bysebuho.com",
                "Referer" to data,
                "X-Embed-Origin" to "javhey.com",
                "X-Embed-Parent" to data,
                "X-Embed-Referer" to "https://javhey.com/",
                "User-Agent" to globalHeaders["User-Agent"]!!,
                "Accept" to "*/*"
            )

            try {
                val jsonResponse = app.get(apiUrl, headers = apiHeaders).parsedSafe<BysebuhoResponse>()
                val finalUrl = jsonResponse?.embed_frame_url
                
                if (finalUrl != null) {
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            "Bysebuho",
                            finalUrl, 
                            data, 
                            Qualities.Unknown.value,
                            isM3u8 = finalUrl.contains(".m3u8")
                        )
                    )
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }

    data class BysebuhoResponse(
        val embed_frame_url: String? = null
    )
}
