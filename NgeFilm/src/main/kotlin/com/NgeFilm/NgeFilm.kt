package com.NgeFilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class NgeFilm : MainAPI() {
    override var mainUrl = "https://ngefilm21.pw"
    override var name = "NgeFilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // === PARSING UTAMA ===
    
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Menggunakan halaman utama sebagai feed
        val document = app.get(mainUrl).document
        val home = document.select("article.item-infinite").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(
            list = HomePageList("Latest Movies", home),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = this.selectFirst("img.attachment-medium")?.attr("src")
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document
        
        return document.select("article.item-infinite").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Selektor Detail
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: "Unknown"
        val desc = document.select("div.entry-content p").text()
        val poster = document.selectFirst("div.gmr-poster img")?.attr("src")
        
        // Tahun (Parsing text dari div.gmr-moviedata)
        val metaText = document.select("div.gmr-moviedata").text()
        val yearRegex = """\d{4}""".toRegex()
        val year = yearRegex.find(metaText)?.value?.toIntOrNull()

        // Mendapatkan link iframe
        // Kita ambil 'src' dari iframe. Ini akan diproses di loadLinks
        val iframeSrc = document.selectFirst("div.gmr-embed-responsive iframe")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, iframeSrc) {
            this.posterUrl = poster
            this.year = year
            this.plot = desc
        }
    }

    // === LOGIKA HANDLING SERVER ===

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        
        var currentUrl = data

        // A. Tahap Unshorten (Wajib untuk Server 2 & 3 / Redirects)
        // Jika link mengandung short.icu atau hglink.to, kita request dulu untuk dapat real URL
        if (currentUrl.contains("short.icu") || currentUrl.contains("hglink.to")) {
            try {
                // app.get(url).url akan memberikan URL akhir setelah redirect
                currentUrl = app.get(currentUrl).url
            } catch (e: Exception) {
                // Jika gagal unshorten, biarkan lanjut siapa tahu bisa dihandle extractor
            }
        }

        // B. Routing Server
        
        // 1. Server 1 (RpmLive) - Gunakan Custom Extractor yang kita buat di Extractors.kt
        if (currentUrl.contains("rpmlive.online")) {
             RpmLive().getUrl(currentUrl, null, subtitleCallback, callback)
        } 
        // 2. Server General (Abyss, Xenolyzb, Kraken, Hxfile, dll)
        // loadExtractor bawaan Cloudstream sudah support server-server ini + ReCaptcha mereka
        else {
            loadExtractor(currentUrl, subtitleCallback, callback)
        }

        return true
    }
}
