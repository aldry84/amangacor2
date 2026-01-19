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

    private fun Element.toSearchResult(): SearchResponse {
        val status = this.select(".bg-blue-800").text()
        val rawTitle = this.select(".text-secondary").text()
        // Format judul: Tambahkan status [Uncensored] dsb jika ada
        val title = if(status.isNotBlank()) "[$status] $rawTitle" else rawTitle
        
        val href = this.select(".text-secondary").attr("href")
        val posterUrl = this.selectFirst(".w-full")?.attr("data-src")
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        
        // PERBAIKAN: Menghapus loop 7 halaman agar tidak berat/spam. 
        // Cukup ambil halaman 1 saja untuk responsivitas maksimal.
        try {
            val document = app.get("$mainUrl/en/search/$query").document
            val results = document.select(".thumbnail").mapNotNull { it.toSearchResult() }
            searchResponse.addAll(results)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // 1. Ambil Metadata Utama
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        // 2. LOGIKA BARU REKOMENDASI (Sapu Jagat)
        // Mencari elemen berdasarkan judul film (text-secondary) lalu mengambil parent-nya (kartu film)
        // Ini mengatasi masalah di mana class CSS berbeda antara Home dan Detail page.
        val recommendations = document.select("div.grid a.text-secondary").mapNotNull { element ->
             var card = element.parent()
             // Naik ke atas sampai ketemu pembungkus utamanya
             if (card != null && !card.className().contains("group") && !card.className().contains("thumbnail")) {
                 card = card.parent()
             }
             card?.toSearchResult()
        }.distinctBy { it.url } // Hapus duplikat

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            // PERBAIKAN: Mengganti nama variabel response agar tidak bentrok dengan parameter 'data'
            val response = app.get(data)
            val doc = response.document
            
            // Logic Video (M3U8 Unpacker)
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

        // Logic Subtitle (SubtitleCat)
        try {
            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
            // Ekstrak kode JAV (misal: ABD-123) dari judul
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
                        // Opsional: break jika sudah ketemu 1 match yang pas, tapi lanjut juga oke
                    }
                }
            }
        } catch (e: Exception) { }

        return true
    }
}
