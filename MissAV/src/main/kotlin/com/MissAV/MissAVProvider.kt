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

    // Fungsi Helper untuk Search & Home
    private fun Element.toSearchResult(): SearchResponse? {
        try {
            val linkElement = this.selectFirst("a") ?: return null
            val href = linkElement.attr("href")
            
            var posterUrl = this.select("img").attr("data-src")
            if (posterUrl.isBlank()) posterUrl = this.select("img").attr("src")

            var title = this.select(".text-secondary").text()
            if (title.isBlank()) title = this.select("img").attr("alt")
            if (title.isBlank()) title = linkElement.attr("title")
            if (title.isBlank()) title = "Unknown Title"

            val status = this.select(".bg-blue-800, span.absolute").text()
            val finalTitle = if(status.isNotBlank()) "[$status] $title" else title
            
            return newMovieSearchResponse(finalTitle, href, TvType.NSFW) {
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
        } catch (e: Exception) { e.printStackTrace() }
        return searchResponse
    }

    // --- BAGIAN INI YANG MEMPERBAIKI KOTAK ABU-ABU (REKOMENDASI) ---
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim() ?: "Unknown"
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        // LOGIKA BARU: Cari Gambar (img) dulu di dalam Grid, baru cari datanya.
        // Ini mengatasi masalah struktur HTML yang berubah-ubah di halaman detail.
        val recommendations = document.select("div.grid img").mapNotNull { img ->
            try {
                // 1. Cari Link (Parent dari gambar)
                val parentLink = img.parents().find { it.tagName() == "a" }
                val href = parentLink?.attr("href") ?: return@mapNotNull null
                
                // 2. Ambil URL Gambar (Cek data-src dulu untuk lazy load)
                val imgUrl = img.attr("data-src").ifBlank { img.attr("src") }
                if (imgUrl.isBlank()) return@mapNotNull null

                // 3. Ambil Judul (Cek Alt, Title, Text)
                var name = img.attr("alt")
                if (name.isBlank()) name = parentLink.attr("title")
                if (name.isBlank()) name = parentLink.text()
                
                // 4. Ambil Status (Uncensored)
                val status = parentLink.parent()?.select(".bg-blue-800, span.absolute")?.text() ?: ""
                val finalTitle = if (status.isNotBlank()) "[$status] $name" else name

                newMovieSearchResponse(finalTitle, href, TvType.NSFW) {
                    this.posterUrl = imgUrl
                }
            } catch (e: Exception) { null }
        }.distinctBy { it.url }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
            val response = app.get(data)
            
            // 1. Video Extractor
            try {
                getAndUnpack(response.text).let { unpackedText ->
                    if (!unpackedText.isNullOrBlank()) {
                        val linkList = unpackedText.split(";")
                        val finalLink = "source='(.*)'".toRegex().find(linkList.firstOrNull() ?: "")?.groups?.get(1)?.value
                        if (!finalLink.isNullOrEmpty()) {
                            callback.invoke(
                                newExtractorLink(name, name, finalLink, ExtractorLinkType.M3U8) {
                                    this.referer = mainUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            // 2. Subtitle Extractor
            try {
                val title = response.document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
                val javCode = "([a-zA-Z]+-\\d+)".toRegex().find(title)?.groups?.get(1)?.value
                
                if(!javCode.isNullOrEmpty()) {
                    val query = "$subtitleCatUrl/index.php?search=$javCode"
                    val subDoc = app.get(query, timeout = 15).document
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
                                        subtitleCallback.invoke(
                                            newSubtitleFile(language.replace("\uD83D\uDC4D \uD83D\uDC4E","").trim(), "$subtitleCatUrl${textElement[0].attr("href")}")
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
