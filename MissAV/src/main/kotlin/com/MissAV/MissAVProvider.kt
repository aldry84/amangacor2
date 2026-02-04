package com.MissAv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class MissAVProvider : MainAPI() {
    override var mainUrl              = "https://missav.ws"
    override var name                 = "MissAV"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded
    
    private val subtitleCatUrl = "https://www.subtitlecat.com"

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to "$mainUrl/"
    )

    override val mainPage = mainPageOf(
            "/dm514/en/new" to "Recent Update",
            "/dm588/en/release" to "New Release",
            "/dm291/en/today-hot" to "Most Viewed Today",
            "/dm169/en/weekly-hot" to "Most Viewed by Week",
            "/dm256/en/monthly-hot" to "Most Viewed by Month",
            "/dm97/en/fc2" to "Uncensored FC2 AV",
            "/dm34/en/madou" to "Madou AV",
            "/dm628/id/uncensored-leak" to "Uncensored Leak",
            "/en/klive" to "Korean Live AV"
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            val document = app.get("$mainUrl${request.data}?page=$page", headers = headers).document
            val responseList  = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = true), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse {
        val status = this.select(".bg-blue-800").text()
        val titleElement = this.select(".text-secondary")
        val rawTitle = titleElement.text()
        
        val title = if(status.isNotBlank()) "[$status] $rawTitle" else rawTitle
        val href = titleElement.attr("href")
        val posterUrl = this.selectFirst(".w-full")?.attr("data-src")
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..2) {
            try {
                val document = app.get("$mainUrl/en/search/$query?page=$i", headers = headers).document
                val results = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
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
        val response = app.get(data, headers = headers)
        val doc = response.document
        
        // --- LOGIKA VIDEO ---
        try {
            getAndUnpack(response.text).let { unpackedText ->
                val m3u8Regex = Regex("""source\s*=\s*['"]([^'"]+\.m3u8[^'"]*)['"]""")
                val match = m3u8Regex.find(unpackedText)
                val m3u8Url = match?.groupValues?.get(1)

                if (m3u8Url != null) {
                    callback.invoke(
                        // FIX: Menggunakan format Lambda { } untuk properti tambahan
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

        // --- LOGIKA SUBTITLE ---
        try {
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            val javCode = Regex("([a-zA-Z]+-\\d+)").find(title)?.groupValues?.get(1)
            
            if (!javCode.isNullOrEmpty()) {
                val query = "$subtitleCatUrl/index.php?search=$javCode"
                val subDoc = app.get(query, headers = headers, timeout = 20).document 
                
                val subList = subDoc.select("td a")
                for (item in subList.take(3)) {
                    if (item.text().contains(javCode, ignoreCase = true)) {
                        val fullUrl = "$subtitleCatUrl/${item.attr("href")}"
                        val pDoc = app.get(fullUrl, headers = headers, timeout = 10).document
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
