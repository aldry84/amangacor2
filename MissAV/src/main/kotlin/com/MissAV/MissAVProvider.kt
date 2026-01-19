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
            "/dm628/id/uncensored-leak" to "Uncensored Leak", // Note: Path menggunakan ID (Indonesia)
            "/en/klive" to "Korean Live AV"
        )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
            // Menggunakan try-catch untuk mencegah crash jika halaman gagal dimuat
            try {
                val document = app.get("$mainUrl${request.data}?page=$page").document
                val responseList  = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
                return newHomePageResponse(HomePageList(request.name, responseList, isHorizontalImages = true), hasNext = true)
            } catch (e: Exception) {
                return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
            }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val status = this.select(".bg-blue-800").text()
        val rawTitle = this.select(".text-secondary").text()
        val title = if(status.isNotBlank()) "[$status] $rawTitle" else rawTitle
        val href = this.select(".text-secondary").attr("href")
        val posterUrl = this.selectFirst(".w-full")?.attr("data-src")
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        
        // REFACTOR: Menghapus loop 1..7 untuk performa. Cukup cari halaman 1.
        // Cloudstream biasanya memanggil search berulang kali jika user scroll ke bawah (jika didukung),
        // tapi untuk basic search, 1 request lebih cepat & stabil.
        try {
            val url = "$mainUrl/en/search/$query"
            val document = app.get(url).document
            val results = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
            searchResponse.addAll(results)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        // PERBAIKAN: Mengganti nama variabel response agar tidak bentrok dengan parameter 'data'
        val response = app.get(data)
        val doc = response.document
        
        // Logic Video
        try {
            getAndUnpack(response.text).let { unpackedText ->
                if (unpackedText.isNullOrBlank()) return@let
                
                val linkList = unpackedText.split(";")
                // Mencoba mencari source m3u8
                val finalLink = "source='(.*)'".toRegex().find(linkList.firstOrNull() ?: "")?.groups?.get(1)?.value
                
                if (!finalLink.isNullOrEmpty()) {
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = finalLink,
                            ExtractorLinkType.M3U8
                        ) {
                            this.referer = mainUrl // Penting untuk anti-hotlink
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Logic Subtitle (SubtitleCat)
        try {
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            val javCode = "([a-zA-Z]+-\\d+)".toRegex().find(title)?.groups?.get(1)?.value
            
            if(!javCode.isNullOrEmpty()) {
                val query = "$subtitleCatUrl/index.php?search=$javCode"
                val subDoc = app.get(query, timeout = 15).document // Timeout diperjelas
                val subList = subDoc.select("td a")
                
                for(item in subList) {
                    if(item.text().contains(javCode, ignoreCase = true)) {
                        val fullUrl = "$subtitleCatUrl/${item.attr("href")}"
                        val pDoc = app.get(fullUrl, timeout = 10).document
                        val sList = pDoc.select(".col-md-6.col-lg-4")
                        
                        for(subItem in sList) {
                            try {
                                val language = subItem.select(".sub-single span:nth-child(2)").text()
                                val textElement = subItem.select(".sub-single span:nth-child(3) a")
                                
                                if(textElement.isNotEmpty() && textElement[0].text() == "Download") {
                                    val downloadUrl = "$subtitleCatUrl${textElement[0].attr("href")}"
                                    
                                    subtitleCallback.invoke(
                                        newSubtitleFile(
                                            language.replace("\uD83D\uDC4D \uD83D\uDC4E","").trim(),
                                            downloadUrl
                                        )
                                    )
                                }
                            } catch (e: Exception) { }
                        }
                        // Break setelah menemukan match pertama untuk menghemat waktu (opsional)
                        // break 
                    }
                }
            }
        } catch (e: Exception) { 
            e.printStackTrace()
        }

        return true
    }
}
