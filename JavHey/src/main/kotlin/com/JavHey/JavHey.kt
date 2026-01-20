package com.JavHey

import android.util.Base64
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

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        // 1. AMBIL GAMBAR (Prioritas dari player dan struktur HTML baru)
        val poster = doc.select("div.product div.images img").attr("src")
            .ifEmpty { doc.select("div.video_player img").attr("src") }
            .ifEmpty { doc.select("meta[property='og:image']").attr("content") }
            .ifEmpty { doc.select("article.post img").attr("src") }

        // 2. AMBIL JUDUL
        var title = doc.select("header.post_header h1").text().trim()
        if (title.isEmpty()) title = doc.select("meta[property='og:title']").attr("content")
        
        val cleanTitle = title
            .replace("JAV Subtitle Indonesia -", "")
            .replace("JAVHEY", "")
            .trim()

        // 3. AMBIL DESKRIPSI
        val description = doc.select("meta[name='description']").attr("content")
            .ifEmpty { doc.select("div.video-description").text() }

        return newMovieLoadResponse(cleanTitle, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        // CARA 1: HARTA KARUN BASE64 (Decode Link Tersembunyi)
        val linksBase64 = doc.select("input#links").attr("value")
        
        if (linksBase64.isNotEmpty()) {
            try {
                // Decode: "https://bysebuho.com/...,,,https://streamwish..."
                val decodedLinks = String(Base64.decode(linksBase64, Base64.DEFAULT))
                val urls = decodedLinks.split(",,,")
                
                urls.forEach { link ->
                    if (link.isNotBlank()) {
                        // LOGIKA BARU: Cek jenis linknya
                        if (link.contains("bysebuho")) {
                            // Kalau link bysebuho, kita bedah manual (Khusus Paling Baru)
                            invokeBysebuho(link, subtitleCallback, callback)
                        } else {
                            // Kalau server lain (Streamwish, dll), serahkan ke Cloudstream
                            loadExtractor(link, subtitleCallback, callback)
                        }
                    }
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // CARA 2: Cadangan jika Base64 kosong (Iframe Manual)
        val iframeSrc = doc.select("iframe#iframe-link").attr("src")
        if (iframeSrc.isNotEmpty()) {
            if (iframeSrc.contains("bysebuho")) {
                invokeBysebuho(iframeSrc, subtitleCallback, callback)
            } else {
                loadExtractor(iframeSrc, subtitleCallback, callback)
            }
            return true
        }

        return false
    }

    // FUNGSI KHUSUS MEMBEDAH BYSEBUHO (KUNCI UTAMA)
    private suspend fun invokeBysebuho(
        iframeUrl: String, 
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        // Ambil Kode: https://bysebuho.com/e/KODE/...
        val code = iframeUrl.substringAfter("/e/").substringBefore("/")
        
        // Panggil API
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
            
            // Dapat link asli (biasanya 9n8o.com atau redirector lain)
            val nextUrl = json.embed_frame_url
            
            if (!nextUrl.isNullOrEmpty()) {
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
