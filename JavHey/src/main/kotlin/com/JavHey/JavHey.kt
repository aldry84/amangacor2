package com.JavHey

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
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

        val poster = doc.select("div.product div.images img").attr("src")
            .ifEmpty { doc.select("div.video_player img").attr("src") }
            .ifEmpty { doc.select("meta[property='og:image']").attr("content") }
            .ifEmpty { doc.select("article.post img").attr("src") }

        var title = doc.select("header.post_header h1").text().trim()
        if (title.isEmpty()) title = doc.select("meta[property='og:title']").attr("content")
        
        val cleanTitle = title
            .replace("JAV Subtitle Indonesia -", "")
            .replace("JAVHEY", "")
            .trim()

        val description = doc.select("meta[name='description']").attr("content")
            .ifEmpty { doc.select("div.video-description").text() }

        return newMovieLoadResponse(cleanTitle, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    // ==========================================
    // LOGIKA PENERJEMAH LINK (MIRROR UNMASKING)
    // ==========================================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document

        val linksBase64 = doc.select("input#links").attr("value")
        
        if (linksBase64.isNotEmpty()) {
            try {
                // Decode teks rahasia
                val decodedLinks = String(Base64.decode(linksBase64, Base64.DEFAULT))
                // Pisahkan daftar link
                val urls = decodedLinks.split(",,,")
                
                urls.forEach { rawLink ->
                    val link = rawLink.trim()
                    if (link.isNotBlank()) {
                        
                        // 1. Turtle4Up (alias StreamWish)
                        // Format: https://turtle4up.top/#KODE
                        if (link.contains("turtle4up.top") || link.contains("t4.top")) {
                            val code = link.substringAfter("#")
                            if (code.isNotEmpty() && code != link) {
                                loadExtractor("https://streamwish.com/e/$code", subtitleCallback, callback)
                            }
                        }
                        
                        // 2. KR21 (alias MixDrop)
                        // Format: https://kr21.click/e/KODE
                        else if (link.contains("kr21.click")) {
                            val code = link.substringAfter("/e/").substringBefore("/")
                            loadExtractor("https://mixdrop.co/e/$code", subtitleCallback, callback)
                        } 
                        
                        // 3. Minochinos (alias LuluStream)
                        // Format: https://minochinos.com/v/KODE
                        else if (link.contains("minochinos.com")) {
                            val code = link.substringAfter("/v/").substringBefore("/")
                            // LuluStream butuh domain asli agar Cloudstream mendeteksinya
                            loadExtractor("https://lulustream.com/e/$code", subtitleCallback, callback)
                        }

                        // 4. Cavanhabg (alias StreamWish)
                        // Format: https://cavanhabg.com/e/KODE
                        else if (link.contains("cavanhabg.com")) {
                            val code = link.substringAfter("/e/").substringBefore("/")
                            loadExtractor("https://streamwish.com/e/$code", subtitleCallback, callback)
                        }

                        // 5. Link Asli Lainnya (Kecuali Bysebuho)
                        else if (!link.contains("bysebuho") && !link.contains("9n8o")) {
                            loadExtractor(link, subtitleCallback, callback)
                        }
                    }
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // CADANGAN IFRAME MANUAL
        val iframeSrc = doc.select("iframe#iframe-link").attr("src")
        if (iframeSrc.isNotEmpty() && !iframeSrc.contains("bysebuho")) {
            loadExtractor(iframeSrc, subtitleCallback, callback)
            return true
        }

        return false
    }
}
