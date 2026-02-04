package com.JavHey

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.Base64

class JavHey : MainAPI() {
    override var mainUrl = "https://javhey.com"
    override var name = "JavHey"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.NSFW)

    // UPDATE DI SINI: Menambahkan kategori baru dengan suffix '/page/' agar pagination berjalan
    override val mainPage = mainPageOf(
        "$mainUrl/videos/paling-dilihat/page=" to "Paling Dilihat",
        "$mainUrl/videos/top-rating/page=" to "Top Rating",
        "$mainUrl/category/12/cuckold-or-ntr/page/" to "CUCKOLD OR NTR VIDEOS",
        "$mainUrl/category/31/decensored/page/" to "DECENSORED VIDEOS",
        "$mainUrl/category/21/drama/page/" to "Drama",
        "$mainUrl/category/114/female-investigator/page/" to "Investigasi",
        "$mainUrl/category/9/housewife/page/" to "HOUSEWIFE",
        "$mainUrl/category/227/hubungan-sedarah/page/" to "Inces",
        "$mainUrl/category/87/hot-spring/page/" to "Air Panas"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // Logika penggabungan URL dengan nomor halaman
        val url = request.data + page
        val document = app.get(url).document
        
        val home = document.select("article.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("div.item_content h3 a") ?: return null
        val title = titleElement.text().trim()
        val href = fixUrl(titleElement.attr("href"))
        
        // LOGIKA BARU: Cari gambar HD
        val posterUrl = this.selectFirst("div.item_header img")?.getHighQualityImageAttr()
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?s=$query"
        val document = app.get(url).document

        return document.select("article.item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.product_title")?.text()?.trim() ?: "No Title"
        val description = document.select("p.video-description").text()
            .replace("Description: ", "", ignoreCase = true).trim()
        
        // LOGIKA BARU: Cari gambar HD di halaman detail
        val poster = document.selectFirst("div.images img")?.getHighQualityImageAttr()
        
        val actors = document.select("div.product_meta a[href*='/actor/']").map { 
            ActorData(Actor(it.text(), "")) 
        }

        val yearText = document.selectFirst("div.product_meta span:contains(Release Day)")?.text()
        val year = yearText?.split(":")?.lastOrNull()?.trim()?.take(4)?.toIntOrNull()
        val tags = document.select("div.product_meta span:contains(Category) a, div.product_meta span:contains(Tag) a")
            .map { it.text() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.actors = actors
            this.year = year
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        val hiddenLinksEncrypted = document.selectFirst("input#links")?.attr("value")
        
        if (!hiddenLinksEncrypted.isNullOrEmpty()) {
            try {
                val decodedBytes = Base64.getDecoder().decode(hiddenLinksEncrypted)
                val decodedString = String(decodedBytes)
                val urls = decodedString.split(",,,")
                
                urls.forEach { sourceUrl ->
                    if (sourceUrl.isNotBlank()) {
                        loadExtractor(sourceUrl, subtitleCallback, callback)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        document.select("div.links-download a").forEach { linkTag ->
            val downloadUrl = linkTag.attr("href")
            if (downloadUrl.isNotBlank()) {
                loadExtractor(downloadUrl, subtitleCallback, callback)
            }
        }

        return true
    }

    // --- HELPER FUNCTION UNTUK GAMBAR HD ---
    
    // Fungsi ekstensi untuk mengambil atribut gambar terbaik
    private fun Element.getHighQualityImageAttr(): String? {
        val url = when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("data-original") -> this.attr("data-original")
            this.hasAttr("srcset") -> this.attr("srcset").substringBefore(" ")
            else -> this.attr("src")
        }
        return url.toHighRes()
    }

    // Fungsi regex untuk membersihkan URL dari ukuran thumbnail
    private fun String?.toHighRes(): String? {
        return this?.replace(Regex("-\\d+x\\d+(?=\\.[a-zA-Z]+$)"), "")
                   ?.replace("-scaled", "")
    }
}
