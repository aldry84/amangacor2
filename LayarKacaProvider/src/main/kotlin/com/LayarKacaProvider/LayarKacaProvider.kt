package com.layarKacaProvider

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
import java.net.URI

class LayarKacaProvider : MainAPI() {

    // Domain Utama (Movie)
    override var mainUrl = "https://tv7.lk21official.cc"
    
    // Domain Khusus Series (Nontondrama)
    private var seriesDomain = "https://tv3.nontondrama.my"
    
    // API Search (Pusat)
    private var searchApiUrl = "https://gudangvape.com"

    override var name = "LayarKaca"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "$mainUrl/populer/page/" to "Film Terplopuler",
        "$mainUrl/rating/page/" to "Film Berdasarkan IMDb Rating",
        "$mainUrl/most-commented/page/" to "Film Dengan Komentar Terbanyak",
        "$mainUrl/latest-series/page/" to "Series Terbaru", // Pake mainUrl dulu, nanti diredirect
        "$mainUrl/series/asian/page/" to "Film Asian Terbaru",
        "$mainUrl/latest/page/" to "Film Upload Terbaru",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data + page).documentLarge
        val home = document.select("article figure").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    // LOGIKA REDIRECT PENTING DI SINI
    private suspend fun getProperLink(url: String): String {
        // 1. Jika URL sudah mengarah ke nontondrama, biarkan saja
        if (url.contains("nontondrama")) return url
        
        // 2. Jika URL mengandung /series/, kemungkinan besar akan redirect
        // Kita coba request HEAD/GET untuk melihat URL akhirnya (follow redirects)
        if (url.contains("/series/")) {
            val res = app.get(url, allowRedirects = true)
            return res.url // Kembalikan URL akhir setelah redirect (misal: ke nontondrama)
        }
        
        return url
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3")?.ownText()?.trim() ?: return null
        // Ambil href mentah
        val rawHref = this.selectFirst("a")!!.attr("href")
        // FixUrl akan menggabungkan dengan mainUrl jika relatif, atau biarkan jika absolut
        val href = fixUrl(rawHref) 
        
        val posterUrl = fixUrlNull(this.selectFirst("img")?.getImageAttr())
        val type = if (this.selectFirst("span.episode") == null) TvType.Movie else TvType.TvSeries
        
        return if (type == TvType.TvSeries) {
            val episode = this.selectFirst("span.episode strong")?.text()?.filter { it.isDigit() }
                ?.toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            val quality = this.select("div.quality").text().trim()
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val res = app.get(
            "$searchApiUrl/search.php?s=$query",
            headers = mapOf(
                "Origin" to mainUrl,
                "Referer" to "$mainUrl/"
            )
        ).text
        
        val results = mutableListOf<SearchResponse>()

        try {
            val root = JSONObject(res)
            if (root.has("data")) {
                val arr = root.getJSONArray("data")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val title = item.getString("title")
                    val slug = item.getString("slug")
                    val type = item.getString("type") 
                    
                    var posterUrl = item.optString("poster")
                    if (!posterUrl.startsWith("http")) {
                        posterUrl = "https://poster.lk21.party/wp-content/uploads/$posterUrl"
                    }

                    // Logika URL Item Search:
                    // Jika tipe series, kita arahkan langsung ke domain nontondrama biar cepat
                    val itemUrl = if (type == "series") {
                        "$seriesDomain/$slug" 
                    } else {
                        "$mainUrl/$slug"
                    }

                    if (type == "series") {
                        results.add(newTvSeriesSearchResponse(title, itemUrl, TvType.TvSeries) {
                            this.posterUrl = posterUrl
                        })
                    } else {
                        results.add(newMovieSearchResponse(title, itemUrl, TvType.Movie) {
                            this.posterUrl = posterUrl
                        })
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LayarKacaSearch", "Error parsing JSON: ${e.message}")
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse {
        // Dapatkan link asli (menangani redirect lk21 -> nontondrama)
        val finalUrl = getProperLink(url)
        
        val document = app.get(finalUrl).documentLarge
        val baseurl = getBaseUrl(finalUrl) // Base URL dinamis (bisa lk21 atau nontondrama)
        
        val title = document.selectFirst("div.movie-info h1")?.text()?.trim().toString()
        val poster = document.select("meta[property=og:image]").attr("content")
        val tags = document.select("div.tag-list span").map { it.text() }
        val posterheaders = mapOf("Referer" to baseurl)

        val year = Regex("\\d, (\\d+)").find(
            document.select("div.movie-info h1").text().trim()
        )?.groupValues?.get(1).toString().toIntOrNull()
        
        val tvType = if (document.selectFirst("#season-data") != null) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div.meta-info")?.text()?.trim()
        val trailer = document.selectFirst("ul.action-left > li:nth-child(3) > a")?.attr("href")
        val rating = document.selectFirst("div.info-tag strong")?.text()

        val recommendations = document.select("li.slider article").map {
            val recName = it.selectFirst("h3")?.text()?.trim().toString()
            val recHref = fixUrl(it.selectFirst("a")!!.attr("href"))
            val recPosterUrl = fixUrl(it.selectFirst("img")?.attr("src").toString())
            newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                this.posterUrl = recPosterUrl
                this.posterHeaders = posterheaders
            }
        }

        return if (tvType == TvType.TvSeries) {
            val json = document.selectFirst("script#season-data")?.data()
            val episodes = mutableListOf<Episode>()
            if (json != null) {
                val root = JSONObject(json)
                root.keys().forEach { seasonKey ->
                    val seasonArr = root.getJSONArray(seasonKey)
                    for (i in 0 until seasonArr.length()) {
                        val ep = seasonArr.getJSONObject(i)
                        // Pastikan URL episode menggunakan baseurl yang benar (nontondrama/lk21)
                        val slug = ep.getString("slug")
                        // Hati-hati double slash
                        val href = if(baseurl.endsWith("/")) "$baseurl$slug" else "$baseurl/$slug"
                        
                        val episodeNo = ep.optInt("episode_no")
                        val seasonNo = ep.optInt("s")
                        episodes.add(
                            newEpisode(href) {
                                this.name = "Episode $episodeNo"
                                this.season = seasonNo
                                this.episode = episodeNo
                            }
                        )
                    }
                }
            }
            newTvSeriesLoadResponse(title, finalUrl, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, finalUrl, TvType.Movie, finalUrl) {
                this.posterUrl = poster
                this.posterHeaders = posterheaders
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Load halaman player
        val document = app.get(data).documentLarge
        
        // Cari server players
        document.select("ul#player-list > li").map {
            fixUrl(it.select("a").attr("href"))
        }.amap {
            // Gunakan 'data' (URL halaman saat ini) sebagai referer saat mencari iframe
            val iframeUrl = it.getIframe(referer = data)
            val extractorReferer = getBaseUrl(it) // Referer untuk extractor
            
            Log.d("Phisher-Debug", "Found iframe: $iframeUrl from $it")
            
            if(iframeUrl.isNotEmpty()) {
                loadExtractor(iframeUrl, extractorReferer, subtitleCallback, callback)
            }
        }
        return true
    }

    // Update getIframe untuk menerima referer dinamis
    private suspend fun String.getIframe(referer: String): String {
        val response = app.get(this, referer = referer)
        val document = response.documentLarge
        val responseText = response.text

        var src = document.selectFirst("div.embed-container iframe")?.attr("src")

        if (src.isNullOrEmpty()) {
            src = document.selectFirst("iframe[src^=http]")?.attr("src")
        }

        if (src.isNullOrEmpty()) {
            val regex = """["'](https?://[^"']+)["']""".toRegex()
            val foundLinks = regex.findAll(responseText).map { it.groupValues[1] }.toList()
            
            src = foundLinks.firstOrNull { link -> 
                !link.contains(".js") && 
                !link.contains(".css") && 
                !link.contains(".png") && 
                !link.contains(".jpg") &&
                (link.contains("embed") || link.contains("player") || link.contains("streaming") || link.contains("hownetwork"))
            }
        }

        return fixUrl(src ?: "")
    }

    private suspend fun fetchURL(url: String): String {
        val res = app.get(url, allowRedirects = false)
        val href = res.headers["location"]

        return if (href != null) {
            val it = URI(href)
            "${it.scheme}://${it.host}"
        } else {
            url
        }
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("src") -> this.attr("src")
            this.hasAttr("data-src") -> this.attr("data-src")
            else -> this.attr("src")
        }
    }

    fun getBaseUrl(url: String?): String {
        return try {
            URI(url).let {
                "${it.scheme}://${it.host}"
            }
        } catch (e: Exception) {
            ""
        }
    }
}
