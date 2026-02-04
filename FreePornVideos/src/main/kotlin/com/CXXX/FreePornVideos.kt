package com.CXXX

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class FreePornVideos : MainAPI() {
    override var mainUrl              = "https://www.freepornvideos.xxx"
    override var name                 = "Free Porn Videos"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    // Perbaikan: Menghapus duplikasi "Brazzers"
    override val mainPage = mainPageOf(
        "most-popular/week" to "Most Popular",
        "networks/brazzers-com" to "Brazzers",
        "networks/mylf-com" to "MYLF",
        "networks/bangbros" to "BangBros",
        "networks/adult-time" to "Adult Time",
        "networks/rk-com" to "Reality Kings",
        "categories/jav-uncensored" to "Jav",
        "networks/mom-lover" to "MILF"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Menggunakan try-catch agar jika halaman gagal dimuat, app tidak crash
        return try {
            val document = app.get("$mainUrl/${request.data}/${page+1}/").document
            val home = document.select("#list_videos_common_videos_list_items > div.item").mapNotNull {
                it.toSearchResult()
            }

            newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = true
                ),
                hasNext = true
            )
        } catch (e: Exception) {
            newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
        }
    }

    // Perbaikan: Menggunakan ?.let dan ?: untuk mencegah crash jika elemen HTML berubah
    private fun Element.toSearchResult(): SearchResponse? {
        val linkTag = this.selectFirst("a") ?: return null
        val href = linkTag.attr("href") ?: return null
        val title = this.select("strong.title").text() ?: "Unknown"
        val posterUrl = linkTag.selectFirst("img")?.getImageAttr()

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    private fun String?.createSlug(): String? {
        return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
            ?.trim()
            ?.replace("\\s+".toRegex(), "-")
            ?.lowercase()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        val searchquery = query.createSlug() ?: return emptyList()

        // Perbaikan: Mengurangi loop agar search lebih cepat. 
        // Mengambil 1 halaman biasanya cukup, atau maksimal 2.
        for (i in 1..1) { 
            try {
                val document = app.get("${mainUrl}/search/$searchquery/$i").document
                val results = document.select("#custom_list_videos_videos_list_search_result_items > div.item")
                    .mapNotNull { it.toSearchResult() }
                
                searchResponse.addAll(results)
                if (results.isEmpty()) break
            } catch (e: Exception) {
                break
            }
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val full_title      = document.selectFirst("div.headline > h1")?.text()?.trim() ?: "Unknown Title"
        val last_index      = full_title.lastIndexOf(" - ")
        val raw_title       = if (last_index != -1) full_title.substring(0, last_index) else full_title
        val title           = raw_title.removePrefix("- ").trim().removeSuffix("-").trim()

        val poster          = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val tags            = document.selectXpath("//div[contains(text(), 'Categories:')]/a").map { it.text() }
        val description     = document.selectXpath("//div[contains(text(), 'Description:')]/em").text().trim()
        val actors          = document.selectXpath("//div[contains(text(), 'Models:')]/a").map { it.text() }
        val recommendations = document.select("div#list_videos_related_videos_items div.item").mapNotNull { it.toSearchResult() }

        // Perbaikan: Mencegah crash jika judul terlalu pendek
        val year = if (full_title.length >= 4) {
             full_title.takeLast(4).toIntOrNull()
        } else null

        val rating          = document.selectFirst("div.rating span")?.text()?.substringBefore("%")?.trim()?.toFloatOrNull()?.div(10)?.toString()

        val raw_duration    = document.selectXpath("//span[contains(text(), 'Duration')]/em").text().trim()
        val duration_parts  = raw_duration.split(":")
        val duration        = when (duration_parts.size) {
            3 -> {
                val hours   = duration_parts[0].toIntOrNull() ?: 0
                val minutes = duration_parts[1].toIntOrNull() ?: 0
                hours * 60 + minutes
            }
            else -> {
                duration_parts[0].toIntOrNull() ?: 0
            }
        }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.recommendations = recommendations
            this.score           = Score.from10(rating)
            this.duration        = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        document.select("video source").forEach { res ->
            val srcUrl = res.attr("src")
            val qualityLabel = res.attr("label")
            
            // Perbaikan: Menjalankan request redirect di dalam try-catch
            // agar jika satu link error, proses tidak berhenti total.
            try {
                // Request HEAD atau GET tanpa body untuk mempercepat jika server mendukungnya
                // Namun karena kita butuh header location, GET standar ok tapi timeout perlu diperhatikan
                val response = app.get(srcUrl, allowRedirects = false)
                val finalUrl = response.headers["location"] ?: srcUrl
                
                callback(
                    newExtractorLink(
                        source = "FPV",
                        name = "FPV $qualityLabel",
                        url = finalUrl,
                        referer = data,
                        quality = getQualityFromName(qualityLabel)
                    )
                )
            } catch (e: Exception) {
                // Log error jika perlu, atau abaikan link yang rusak
                // e.printStackTrace()
            }
        }

        return true
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }
}
