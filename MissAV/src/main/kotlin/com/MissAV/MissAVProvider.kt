package com.MissAv

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

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
            try {
                val document = app.get("$mainUrl${request.data}?page=$page").document
                val responseList  = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
                return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = true), hasNext = true)
            } catch (e: Exception) {
                return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
            }
    }

    // --- FUNGSI PENCARI DATA YANG SUDAH DIPERBAIKI (LEBIH PINTAR) ---
    private fun Element.toSearchResult(): SearchResponse? {
        try {
            // 1. Ambil Link
            val linkElement = this.selectFirst("a") ?: return null
            val href = linkElement.attr("href")
            if (href.isBlank()) return null

            // 2. Ambil Status (Uncensored, dll)
            // Mencari badge status dengan beberapa kemungkinan class
            val status = this.select(".bg-blue-800, .absolute.top-2.right-2, span.absolute").text()

            // 3. Ambil Judul (Dengan Fallback/Cadangan)
            var rawTitle = this.select(".text-secondary").text() // Coba cari di text biasa
            if (rawTitle.isBlank()) {
                rawTitle = this.select("img").attr("alt") // Fallback 1: Alt gambar
            }
            if (rawTitle.isBlank()) {
                rawTitle = linkElement.attr("title") // Fallback 2: Title link
            }
            if (rawTitle.isBlank()) rawTitle = "Unknown Title" // Fallback terakhir

            val title = if(status.isNotBlank()) "[$status] $rawTitle" else rawTitle
            
            // 4. Ambil Gambar (Dengan Fallback)
            var posterUrl = this.select("img").attr("data-src") // Coba cari data-src (lazy load)
            if (posterUrl.isNullOrBlank()) {
                posterUrl = this.select("img").attr("src") // Fallback: src biasa
            }
            
            return newMovieSearchResponse(title, href, TvType.NSFW) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        try {
            val document = app.get("$mainUrl/en/search/$query").document
            val results = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
            searchResponse.addAll(results)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return searchResponse
    }

    // --- FUNGSI LOAD DENGAN SELECTOR SAPU JAGAT ---
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Metadata Utama
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        // Logika Rekomendasi (Universal Selector)
        // Kita cari DIV apapun di dalam Grid yang memiliki IMG dan Link (A)
        // Ini menghindari masalah salah nama class CSS
        val recommendations = document.select("div.grid div, div.w-full div.relative").mapNotNull { element ->
             if (element.select("img").isNotEmpty() && element.select("a").isNotEmpty()) {
                 element.toSearchResult()
             } else {
                 null
             }
        }.distinctBy { it.url }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            val response = app.get(data)
            val doc = response.document
            
            // 1. Ekstrak Video (M3U8)
            try {
                getAndUnpack(response.text).let { unpackedText ->
                    if (!unpackedText.isNullOrBlank()) {
                        val linkList = unpackedText.split(";")
                        val finalLink = "source='(.*)'".toRegex().find(linkList.firstOrNull() ?: "")?.groups?.get(1)?.value
                        
                        if (!finalLink.isNullOrEmpty()) {
                            callback.invoke(
                                newExtractorLink(
                                    source = name,
                                    name = name,
                                    url = finalLink,
                                    ExtractorLinkType.M3U8
                                ) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

        // 2. Ekstrak Subtitle (SubtitleCat)
        try {
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            val javCode = "([a-zA-Z]+-\\d+)".toRegex().find(title)?.groups?.get(1)?.value
            
            if(!javCode.isNullOrEmpty())
            {
                val query = "$subtitleCatUrl/index.php?search=$javCode"
                val subDoc = app.get(query, timeout = 15).document
                val subList = subDoc.select("td a")
                
                for(item in subList)
                {
                    if(item.text().contains(javCode, ignoreCase = true))
                    {
                        val fullUrl = "$subtitleCatUrl/${item.attr("href")}"
                        val pDoc = app.get(fullUrl, timeout = 10).document
                        val sList = pDoc.select(".col-md-6.col-lg-4")
                        
                        for(subItem in sList)
                        {
                            try {
                                val language = subItem.select(".sub-single span:nth-child(2)").text()
                                val textElement = subItem.select(".sub-single span:nth-child(3) a")
                                
                                if(textElement.isNotEmpty() && textElement[0].text() == "Download")
                                {
                                    val url = "$subtitleCatUrl${textElement[0].attr("href")}"
                                    subtitleCallback.invoke(
                                        newSubtitleFile(
                                            language.replace("\uD83D\uDC4D \uD83D\uDC4E","").trim(),
                                            url
                                        )
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
